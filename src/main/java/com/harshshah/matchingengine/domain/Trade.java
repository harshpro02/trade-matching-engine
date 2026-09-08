package com.harshshah.matchingengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record that two orders crossed: {@code quantity} units of {@code symbol}
 * changed hands at {@code price}.
 *
 * <p>Trades are never updated or deleted. They are the audit trail, and every position
 * in the system is derivable by replaying them.
 */
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "symbol", nullable = false, updatable = false, length = 32)
    private String symbol;

    @Column(name = "buy_order_id", nullable = false, updatable = false)
    private UUID buyOrderId;

    @Column(name = "sell_order_id", nullable = false, updatable = false)
    private UUID sellOrderId;

    /** Always the resting order's price. See MatchingEngine for why. */
    @Column(name = "price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, updatable = false)
    private long quantity;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    /** Database-assigned, gives trades a stable total order for replay and paging. */
    @Generated(event = EventType.INSERT)
    @Column(name = "sequence_number", insertable = false, updatable = false)
    private Long sequenceNumber;

    protected Trade() {
        // for JPA
    }

    public Trade(String symbol, UUID buyOrderId, UUID sellOrderId,
                 BigDecimal price, long quantity, Instant executedAt) {
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public UUID getBuyOrderId() {
        return buyOrderId;
    }

    public UUID getSellOrderId() {
        return sellOrderId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "Trade[" + quantity + " " + symbol + " @" + price + "]";
    }
}
