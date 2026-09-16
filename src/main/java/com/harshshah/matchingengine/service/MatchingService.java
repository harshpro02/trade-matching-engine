package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Position;
import com.harshshah.matchingengine.domain.Side;
import com.harshshah.matchingengine.domain.Trade;
import com.harshshah.matchingengine.dto.OrderResponse;
import com.harshshah.matchingengine.dto.PageResponse;
import com.harshshah.matchingengine.dto.SubmitOrderRequest;
import com.harshshah.matchingengine.dto.SubmitOrderResponse;
import com.harshshah.matchingengine.dto.TradeResponse;
import com.harshshah.matchingengine.repository.InstrumentRepository;
import com.harshshah.matchingengine.repository.OrderRepository;
import com.harshshah.matchingengine.repository.PositionRepository;
import com.harshshah.matchingengine.repository.TradeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Transactional
    public SubmitOrderResponse submitOrder(SubmitOrderRequest request) {
        lockBook(request.symbol());
        Instant now = clock.instant();

        Order incoming = request.type() == OrderType.LIMIT
                ? Order.limit(request.accountId(), request.symbol(), request.side(),
                              request.price(), request.quantity(), now)
                : Order.market(request.accountId(), request.symbol(), request.side(),
                               request.quantity(), now);

        // Flushed before matching so trades have an order id to reference and the database
        // has assigned the sequence number that fixes this order's place in the queue.
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

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        lockBook(order.getSymbol());

        // Loaded before the lock was held, so another session may have filled it since.
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
    public PageResponse<OrderResponse> ordersForAccount(UUID accountId, int page, int size) {
        return PageResponse.of(
                orders.findByAccountIdOrderBySequenceNumberDesc(accountId, PageRequest.of(page, size)),
                OrderResponse::from);
    }

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
