package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.dto.ExecutedTradeResponse;
import com.harshshah.matchingengine.dto.OrderBookResponse;
import com.harshshah.matchingengine.dto.PositionResponse;
import com.harshshah.matchingengine.dto.PriceLevelResponse;
import com.harshshah.matchingengine.repository.InstrumentRepository;
import com.harshshah.matchingengine.repository.OrderRepository;
import com.harshshah.matchingengine.repository.PositionRepository;
import com.harshshah.matchingengine.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only views: the book, the tape, and an account's positions.
 *
 * <p>None of these take the per-symbol lock. They are snapshots, and a snapshot that was
 * accurate a millisecond ago is the most any reader can be given anyway; blocking the
 * matching path to serve a page would trade real throughput for an illusion of freshness.
 */
@Service
public class BookService {

    private final OrderRepository orders;
    private final TradeRepository trades;
    private final PositionRepository positions;
    private final InstrumentRepository instruments;

    public BookService(OrderRepository orders,
                       TradeRepository trades,
                       PositionRepository positions,
                       InstrumentRepository instruments) {
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.instruments = instruments;
    }

    /**
     * The current book for a symbol, aggregated by price level.
     *
     * <p>Individual orders are not exposed. A venue publishes depth, not the identity of
     * who is standing in the queue, and aggregating here means a book with ten thousand
     * resting orders still answers in a handful of price levels.
     */
    @Transactional(readOnly = true)
    public OrderBookResponse book(String symbol) {
        if (!instruments.existsById(symbol)) {
            throw new UnknownSymbolException(symbol);
        }
        return new OrderBookResponse(symbol,
                aggregate(orders.findRestingBids(symbol)),
                aggregate(orders.findRestingAsks(symbol)));
    }

    @Transactional(readOnly = true)
    public List<ExecutedTradeResponse> tradesFor(String symbol) {
        if (!instruments.existsById(symbol)) {
            throw new UnknownSymbolException(symbol);
        }
        return trades.findBySymbolOrderBySequenceNumberDesc(symbol).stream()
                .map(ExecutedTradeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> positionsFor(UUID accountId) {
        return positions.findByAccountId(accountId).stream()
                .map(PositionResponse::from)
                .toList();
    }

    /**
     * Collapse an already-ordered list of resting orders into price levels, preserving the
     * order it arrived in - which is best-price-first, because that is how the query sorted
     * it. Aggregating does not re-sort, for the same reason the matcher does not.
     *
     * <p>The map is keyed on the price with trailing zeros stripped, because {@code 150.20}
     * and {@code 150.2000} are the same price level but not equal {@link BigDecimal}s. The
     * first spelling seen is the one reported.
     */
    private static List<PriceLevelResponse> aggregate(List<Order> side) {
        Map<BigDecimal, Level> levels = new LinkedHashMap<>();
        for (Order order : side) {
            BigDecimal key = order.getPrice().stripTrailingZeros();
            levels.computeIfAbsent(key, unused -> new Level(order.getPrice()))
                    .add(order.getRemainingQuantity());
        }
        List<PriceLevelResponse> aggregated = new ArrayList<>(levels.size());
        for (Level level : levels.values()) {
            aggregated.add(new PriceLevelResponse(level.price, level.quantity, level.orderCount));
        }
        return List.copyOf(aggregated);
    }

    private static final class Level {
        private final BigDecimal price;
        private long quantity;
        private int orderCount;

        private Level(BigDecimal price) {
            this.price = price;
        }

        private void add(long remaining) {
            quantity += remaining;
            orderCount++;
        }
    }
}
