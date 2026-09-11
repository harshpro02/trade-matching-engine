package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Position;
import com.harshshah.matchingengine.domain.Side;
import com.harshshah.matchingengine.domain.Trade;
import com.harshshah.matchingengine.dto.OrderResponse;
import com.harshshah.matchingengine.dto.SubmitOrderRequest;
import com.harshshah.matchingengine.dto.SubmitOrderResponse;
import com.harshshah.matchingengine.dto.TradeResponse;
import com.harshshah.matchingengine.repository.InstrumentRepository;
import com.harshshah.matchingengine.repository.OrderRepository;
import com.harshshah.matchingengine.repository.PositionRepository;
import com.harshshah.matchingengine.repository.TradeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything that mutates the book: submitting an order, and cancelling one.
 *
 * <p><b>Transaction boundary.</b> One submission that produces three fills must write all
 * three trades and all six position updates, or none of them. A partial write leaves the
 * book inconsistent - a quantity decremented with no trade to show for it, or a trade with
 * no matching position change - and there is no safe way to repair that afterwards, because
 * nothing records what the intended end state was. If the process dies mid-match the
 * transaction rolls back and the order is simply never acknowledged.
 *
 * <p><b>Locking.</b> Every path takes {@code SELECT ... FOR UPDATE} on the symbol's
 * {@code instruments} row <i>before</i> reading the book. That serialises all matching for
 * one symbol while leaving other symbols fully parallel, and it cannot deadlock because
 * there is only ever one lock to take. Locking the resting orders instead would deadlock
 * when two sessions lock overlapping sets in different orders, and would still be wrong:
 * {@code ORDER BY ... LIMIT n FOR UPDATE} re-evaluates rows after the lock is granted, so
 * two sessions can disagree about which n orders are the best n.
 *
 * <p>The cost is explicit: one symbol matches one order at a time. That is the right trade,
 * because the parallelism that matters here is across symbols, and it is preserved.
 */
@Service
public class MatchingService {

    private final InstrumentRepository instruments;
    private final OrderRepository orders;
    private final TradeRepository trades;
    private final PositionRepository positions;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public MatchingService(InstrumentRepository instruments,
                           OrderRepository orders,
                           TradeRepository trades,
                           PositionRepository positions,
                           Clock clock) {
        this.instruments = instruments;
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.clock = clock;
    }

    /**
     * Accept an order, match it against the book, and persist everything that results.
     *
     * <p>A LIMIT order with quantity left over rests on the book. A MARKET order with
     * quantity left over is cancelled instead, because it has no price at which it could sit.
     */
    @Transactional
    public SubmitOrderResponse submitOrder(SubmitOrderRequest request) {
        lockBook(request.symbol());
        Instant now = clock.instant();

        Order incoming = request.type() == OrderType.LIMIT
                ? Order.limit(request.accountId(), request.symbol(), request.side(),
                              request.price(), request.quantity(), now)
                : Order.market(request.accountId(), request.symbol(), request.side(),
                               request.quantity(), now);

        // Flushed before matching so the trade rows have a real order id to point at, and
        // so the database assigns the sequence number that gives this order its place in
        // the queue. Both are needed before any fill can be written.
        orders.saveAndFlush(incoming);

        List<Order> book = orders.lockRestingOrders(request.symbol(), request.side().opposite());
        List<Fill> fills = OrderBookMatcher.match(incoming, book);

        List<TradeResponse> executed = new ArrayList<>(fills.size());
        for (Fill fill : fills) {
            executed.add(execute(incoming, fill, now));
        }

        if (incoming.getType() == OrderType.MARKET && incoming.getRemainingQuantity() > 0) {
            incoming.cancel();
        }
        orders.save(incoming);
        return SubmitOrderResponse.of(incoming, executed);
    }

    /**
     * Take a resting order off the book.
     *
     * <p>Takes the same per-symbol lock as matching, so a cancel cannot race a fill: either
     * the order is filled and the cancel is rejected, or it is cancelled and the matcher
     * never sees it. There is no interleaving in which both happen.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        lockBook(order.getSymbol());

        // The read above happened before the lock, so another session could have filled
        // this order in between. Refresh to see the state as it is now that we hold it.
        entityManager.refresh(order);

        order.cancel();
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return orders.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> ordersForAccount(UUID accountId) {
        return orders.findByAccountIdOrderBySequenceNumberDesc(accountId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Apply one fill: decrement both orders, write the trade, move both positions.
     *
     * <p>The order of these matters less than the fact that they are all inside the caller's
     * transaction. Any one of them failing takes the rest with it.
     */
    private TradeResponse execute(Order incoming, Fill fill, Instant now) {
        Order resting = fill.restingOrder();
        long quantity = fill.quantity();
        BigDecimal price = fill.price();

        incoming.fill(quantity);
        resting.fill(quantity);
        orders.save(resting);

        boolean incomingIsBuyer = incoming.getSide() == Side.BUY;
        Order buyOrder = incomingIsBuyer ? incoming : resting;
        Order sellOrder = incomingIsBuyer ? resting : incoming;

        Trade trade = trades.save(new Trade(incoming.getSymbol(), buyOrder.getId(),
                sellOrder.getId(), price, quantity, now));

        applyToPosition(buyOrder.getAccountId(), incoming.getSymbol(), Side.BUY, quantity, price);
        applyToPosition(sellOrder.getAccountId(), incoming.getSymbol(), Side.SELL, quantity, price);

        return TradeResponse.from(trade);
    }

    /**
     * Move one account's position by one side of one fill, creating it if this is the first
     * time the account has traded the symbol.
     *
     * <p>When an account trades with itself both calls land on the same managed entity, so
     * the two sides net out exactly as they should rather than one overwriting the other.
     */
    private void applyToPosition(UUID accountId, String symbol, Side side,
                                 long quantity, BigDecimal price) {
        Position position = positions.findByAccountIdAndSymbol(accountId, symbol)
                .orElseGet(() -> positions.saveAndFlush(new Position(accountId, symbol)));
        position.applyFill(side, quantity, price);
        positions.save(position);
    }

    private void lockBook(String symbol) {
        instruments.findAndLockBySymbol(symbol)
                .orElseThrow(() -> new UnknownSymbolException(symbol));
    }
}
