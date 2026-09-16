package com.harshshah.matchingengine;

import com.harshshah.matchingengine.domain.Instrument;
import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Position;
import com.harshshah.matchingengine.domain.Side;
import com.harshshah.matchingengine.dto.OrderBookResponse;
import com.harshshah.matchingengine.dto.SubmitOrderRequest;
import com.harshshah.matchingengine.dto.SubmitOrderResponse;
import com.harshshah.matchingengine.repository.InstrumentRepository;
import com.harshshah.matchingengine.repository.OrderRepository;
import com.harshshah.matchingengine.repository.PositionRepository;
import com.harshshah.matchingengine.repository.TradeRepository;
import com.harshshah.matchingengine.service.BookService;
import com.harshshah.matchingengine.service.MatchingService;
import com.harshshah.matchingengine.service.UnknownSymbolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The matching path end to end against real Postgres.
 *
 * <p>These cannot be unit tests. Time priority depends on an identity column the database
 * assigns, and the concurrency guarantee depends on {@code SELECT ... FOR UPDATE} actually
 * blocking a second session - neither of which exists outside a real database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MatchingServiceIT {

    private static final String SYMBOL = "AAPL";

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private BookService bookService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetBook() {
        jdbcTemplate.execute("TRUNCATE TABLE trades, positions, orders RESTART IDENTITY CASCADE");
        if (!instrumentRepository.existsById(SYMBOL)) {
            instrumentRepository.save(new Instrument(SYMBOL, Instant.now()));
        }
    }

    private SubmitOrderResponse limit(UUID account, Side side, String price, long quantity) {
        return matchingService.submitOrder(new SubmitOrderRequest(
                account, SYMBOL, side, OrderType.LIMIT, new BigDecimal(price), quantity));
    }

    private SubmitOrderResponse market(UUID account, Side side, long quantity) {
        return matchingService.submitOrder(new SubmitOrderRequest(
                account, SYMBOL, side, OrderType.MARKET, null, quantity));
    }

    private Position positionOf(UUID account) {
        return positionRepository.findByAccountIdAndSymbol(account, SYMBOL).orElseThrow();
    }

    @Test
    @DisplayName("an order that does not cross simply rests")
    void anOrderThatDoesNotCrossRests() {
        SubmitOrderResponse response = limit(UUID.randomUUID(), Side.BUY, "149.00", 100);

        assertThat(response.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.trades()).isEmpty();
        assertThat(response.remainingQuantity()).isEqualTo(100);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    @DisplayName("a crossing order trades, and both accounts move")
    void aCrossingOrderProducesATradeAndMovesBothPositions() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        limit(seller, Side.SELL, "150.00", 100);
        SubmitOrderResponse response = limit(buyer, Side.BUY, "150.00", 100);

        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.trades()).hasSize(1);
        assertThat(response.trades().getFirst().price()).isEqualByComparingTo("150.00");
        assertThat(response.trades().getFirst().quantity()).isEqualTo(100);

        assertThat(positionOf(buyer).getQuantity()).isEqualTo(100);
        assertThat(positionOf(buyer).getAverageCost()).isEqualByComparingTo("150.00");
        assertThat(positionOf(seller).getQuantity()).isEqualTo(-100);
        assertThat(positionOf(seller).getAverageCost()).isEqualByComparingTo("150.00");

        // Nothing is realised yet: both sides only opened.
        assertThat(positionOf(buyer).getRealisedPnl()).isEqualByComparingTo("0");
        assertThat(positionOf(seller).getRealisedPnl()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the trade executes at the resting order's price, not the incoming one's")
    void theTradeExecutesAtTheRestingPrice() {
        limit(UUID.randomUUID(), Side.SELL, "150.00", 100);

        SubmitOrderResponse response = limit(UUID.randomUUID(), Side.BUY, "155.00", 100);

        assertThat(response.trades().getFirst().price()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("a limit order's remainder rests; a market order's is cancelled")
    void remaindersAreHandledByType() {
        limit(UUID.randomUUID(), Side.SELL, "150.00", 40);
        SubmitOrderResponse restingRemainder = limit(UUID.randomUUID(), Side.BUY, "150.00", 100);

        assertThat(restingRemainder.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(restingRemainder.remainingQuantity()).isEqualTo(60);

        resetBook();

        limit(UUID.randomUUID(), Side.SELL, "150.00", 40);
        SubmitOrderResponse cancelledRemainder = market(UUID.randomUUID(), Side.BUY, 100);

        // A market order has no price at which it could sit, so the remainder is killed.
        assertThat(cancelledRemainder.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelledRemainder.filledQuantity()).isEqualTo(40);
        assertThat(cancelledRemainder.remainingQuantity()).isEqualTo(60);
    }

    @Test
    @DisplayName("price priority: the better-priced resting order fills first")
    void theBetterPricedRestingOrderFillsFirst() {
        UUID dear = UUID.randomUUID();
        UUID cheap = UUID.randomUUID();

        limit(dear, Side.SELL, "151.00", 100);   // arrives first, but is worse for a buyer
        limit(cheap, Side.SELL, "150.00", 100);

        SubmitOrderResponse response = limit(UUID.randomUUID(), Side.BUY, "151.00", 100);

        assertThat(response.trades()).hasSize(1);
        assertThat(response.trades().getFirst().price()).isEqualByComparingTo("150.00");
        assertThat(positionOf(cheap).getQuantity()).isEqualTo(-100);

        // The dear seller never traded, so it has no position row at all - positions are
        // created by the first fill, not by submitting an order. Its order is still resting,
        // which is the part that actually shows price priority was respected.
        assertThat(positionRepository.findByAccountIdAndSymbol(dear, SYMBOL)).isEmpty();
        assertThat(bookService.book(SYMBOL).asks())
                .singleElement()
                .satisfies(level -> {
                    assertThat(level.price()).isEqualByComparingTo("151.00");
                    assertThat(level.quantity()).isEqualTo(100);
                });
    }

    @Test
    @DisplayName("time priority: at equal prices the earlier order fills first")
    void atEqualPricesTheEarlierOrderFillsFirst() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        limit(first, Side.SELL, "150.00", 100);
        limit(second, Side.SELL, "150.00", 100);

        limit(UUID.randomUUID(), Side.BUY, "150.00", 100);

        // Same price, so the sequence number decides - and it is assigned by the database,
        // not read off a clock, so two orders in the same millisecond still have an order.
        assertThat(positionOf(first).getQuantity()).isEqualTo(-100);
        assertThat(positionRepository.findByAccountIdAndSymbol(second, SYMBOL)).isEmpty();
    }

    @Test
    @DisplayName("an order walks successive price levels, writing one trade per level")
    void anOrderWalksSuccessivePriceLevels() {
        limit(UUID.randomUUID(), Side.SELL, "150.00", 100);
        limit(UUID.randomUUID(), Side.SELL, "151.00", 100);
        limit(UUID.randomUUID(), Side.SELL, "152.00", 100);

        SubmitOrderResponse response = limit(UUID.randomUUID(), Side.BUY, "151.00", 250);

        // 152.00 is too dear, so only two levels trade and 50 of the buy rests.
        assertThat(response.trades()).hasSize(2);
        assertThat(response.filledQuantity()).isEqualTo(200);
        assertThat(response.remainingQuantity()).isEqualTo(50);
        assertThat(response.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    @Test
    @DisplayName("a round trip realises equal and opposite P&L on the two accounts")
    void aRoundTripRealisesEqualAndOppositePnl() {
        UUID shortSeller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        limit(shortSeller, Side.SELL, "150.00", 100);
        limit(buyer, Side.BUY, "150.00", 100);

        limit(buyer, Side.SELL, "140.00", 100);
        limit(shortSeller, Side.BUY, "140.00", 100);

        // Sold at 150 and bought back at 140: the short made 1000, the long lost 1000.
        assertThat(positionOf(shortSeller).getRealisedPnl()).isEqualByComparingTo("1000.0000");
        assertThat(positionOf(buyer).getRealisedPnl()).isEqualByComparingTo("-1000.0000");
        assertThat(positionOf(shortSeller).isFlat()).isTrue();
        assertThat(positionOf(buyer).isFlat()).isTrue();

        // A closed market is zero sum. If these ever stop summing to zero, money is
        // being created or destroyed somewhere in the fill path.
        assertThat(positionOf(shortSeller).getRealisedPnl()
                .add(positionOf(buyer).getRealisedPnl())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the book endpoint aggregates by price level, best first")
    void theBookAggregatesByPriceLevel() {
        limit(UUID.randomUUID(), Side.BUY, "149.00", 100);
        limit(UUID.randomUUID(), Side.BUY, "149.00", 50);
        limit(UUID.randomUUID(), Side.BUY, "148.00", 70);
        limit(UUID.randomUUID(), Side.SELL, "151.00", 30);

        OrderBookResponse book = bookService.book(SYMBOL);

        assertThat(book.bids()).hasSize(2);
        assertThat(book.bids().getFirst().price()).isEqualByComparingTo("149.00");
        assertThat(book.bids().getFirst().quantity()).isEqualTo(150);
        assertThat(book.bids().getFirst().orderCount()).isEqualTo(2);
        assertThat(book.bids().get(1).price()).isEqualByComparingTo("148.00");
        assertThat(book.asks()).hasSize(1);
        assertThat(book.asks().getFirst().quantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("cancelling takes a resting order off the book; cancelling a filled one is rejected")
    void cancellingBehaviour() {
        SubmitOrderResponse resting = limit(UUID.randomUUID(), Side.BUY, "149.00", 100);

        matchingService.cancelOrder(resting.orderId());

        assertThat(orderRepository.findById(resting.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(bookService.book(SYMBOL).bids()).isEmpty();

        // Cancelling it again is a conflict, not a no-op: the caller believed something
        // about the order that is not true, and silently succeeding would hide that.
        assertThatThrownBy(() -> matchingService.cancelOrder(resting.orderId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a cancelled order is invisible to the matcher")
    void aCancelledOrderIsNotMatched() {
        SubmitOrderResponse resting = limit(UUID.randomUUID(), Side.SELL, "150.00", 100);
        matchingService.cancelOrder(resting.orderId());

        SubmitOrderResponse incoming = limit(UUID.randomUUID(), Side.BUY, "150.00", 100);

        assertThat(incoming.trades()).isEmpty();
        assertThat(incoming.status()).isEqualTo(OrderStatus.OPEN);
    }

    @Test
    @DisplayName("an unknown symbol is rejected before anything is written")
    void anUnknownSymbolIsRejected() {
        SubmitOrderRequest request = new SubmitOrderRequest(UUID.randomUUID(), "NOPE",
                Side.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);

        assertThatThrownBy(() -> matchingService.submitOrder(request))
                .isInstanceOf(UnknownSymbolException.class);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("concurrent buyers cannot overfill one resting order")
    void concurrentBuyersCannotOverfillOneRestingOrder() throws Exception {
        UUID seller = UUID.randomUUID();
        limit(seller, Side.SELL, "150.00", 100);

        int buyers = 8;
        CyclicBarrier startTogether = new CyclicBarrier(buyers);
        AtomicInteger failures = new AtomicInteger();

        List<Callable<SubmitOrderResponse>> submissions = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            submissions.add(() -> {
                startTogether.await(20, TimeUnit.SECONDS);
                try {
                    return limit(UUID.randomUUID(), Side.BUY, "150.00", 100);
                } catch (RuntimeException failed) {
                    failures.incrementAndGet();
                    throw failed;
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        try {
            List<Future<SubmitOrderResponse>> results = pool.invokeAll(submissions, 60, TimeUnit.SECONDS);
            long filled = 0;
            for (Future<SubmitOrderResponse> result : results) {
                filled += result.get().filledQuantity();
            }

            // Eight buyers wanted 800 units. Exactly 100 existed. The per-symbol lock is
            // what makes that come out at 100 rather than some racing subset of 800.
            assertThat(filled).isEqualTo(100);
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures.get()).isZero();

        Order restingSell = orderRepository.findByAccountIdOrderBySequenceNumberDesc(seller).getFirst();
        assertThat(restingSell.getRemainingQuantity()).isZero();
        assertThat(restingSell.getStatus()).isEqualTo(OrderStatus.FILLED);

        assertThat(positionOf(seller).getQuantity()).isEqualTo(-100);
        assertThat(tradeRepository.findBySymbolOrderBySequenceNumberDesc(SYMBOL).stream()
                .mapToLong(trade -> trade.getQuantity()).sum()).isEqualTo(100);
    }
}
