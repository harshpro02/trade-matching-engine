package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    /** Executed trades for one symbol, most recent first. */
    List<Trade> findBySymbolOrderBySequenceNumberDesc(String symbol);

    /** Every trade either side of which belongs to the given order. */
    List<Trade> findByBuyOrderIdOrSellOrderIdOrderBySequenceNumberAsc(UUID buyOrderId, UUID sellOrderId);
}
