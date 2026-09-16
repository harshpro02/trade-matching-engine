package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
    Page<Trade> findBySymbolOrderBySequenceNumberDesc(String symbol, Pageable pageable);

    List<Trade> findByBuyOrderIdOrSellOrderIdOrderBySequenceNumberAsc(UUID buyOrderId, UUID sellOrderId);
}
