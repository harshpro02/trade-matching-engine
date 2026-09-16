package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Instrument;
import com.harshshah.matchingengine.dto.ExecutedTradeResponse;
import com.harshshah.matchingengine.dto.InstrumentResponse;
import com.harshshah.matchingengine.dto.OrderBookResponse;
import com.harshshah.matchingengine.dto.PageResponse;
import com.harshshah.matchingengine.dto.PositionResponse;
import com.harshshah.matchingengine.repository.InstrumentRepository;
import com.harshshah.matchingengine.repository.OrderRepository;
import com.harshshah.matchingengine.repository.PositionRepository;
import com.harshshah.matchingengine.repository.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class BookService {
    public static final int DEFAULT_DEPTH = 10;
    public static final int MAX_DEPTH = 50;

    private final OrderRepository orders;
    private final TradeRepository trades;
    private final PositionRepository positions;
    private final InstrumentRepository instruments;
    private final Clock clock;

    public BookService(OrderRepository orders,
                       TradeRepository trades,
                       PositionRepository positions,
                       InstrumentRepository instruments,
                       Clock clock) {
        this.orders = orders;
        this.trades = trades;
        this.positions = positions;
        this.instruments = instruments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderBookResponse book(String symbol, int depth) {
        requireKnown(symbol);
        Pageable levels = PageRequest.of(0, clampDepth(depth));
        return new OrderBookResponse(symbol,
                orders.aggregateBids(symbol, levels),
                orders.aggregateAsks(symbol, levels));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutedTradeResponse> tradesFor(String symbol, int page, int size) {
        requireKnown(symbol);
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.of(
                trades.findBySymbolOrderBySequenceNumberDesc(symbol, pageable),
                ExecutedTradeResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> positionsFor(UUID accountId) {
        return positions.findByAccountId(accountId).stream()
                .map(PositionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InstrumentResponse> instruments() {
        return instruments.findAll(Sort.by("symbol")).stream()
                .map(InstrumentResponse::from)
                .toList();
    }

    @Transactional
    public InstrumentResponse createInstrument(String symbol) {
        if (instruments.existsById(symbol)) {
            throw new SymbolAlreadyExistsException(symbol);
        }
        return InstrumentResponse.from(
                instruments.save(new Instrument(symbol, clock.instant())));
    }

    private void requireKnown(String symbol) {
        if (!instruments.existsById(symbol)) {
            throw new UnknownSymbolException(symbol);
        }
    }

    private static int clampDepth(int depth) {
        return Math.min(Math.max(depth, 1), MAX_DEPTH);
    }
}
