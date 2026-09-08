package com.harshshah.matchingengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single order to buy or sell a quantity of one symbol.
 *
 * <p>An order is created with {@code remainingQuantity == quantity} and is decremented
 * as it fills. It rests on the book for as long as {@link OrderStatus#isResting()} holds.
 *
 * <p><b>Time priority is carried by {@link #sequenceNumber}, not {@link #createdAt}.</b>
 * {@code createdAt} is a wall clock reading and two orders can easily share one; the
 * sequence number comes from a database sequence, so it is monotonic and total. Ordering
 * the book by {@code (price, sequenceNumber)} therefore gives a deterministic queue where
 * ordering by timestamp alone would leave ties to be broken arbitrarily.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "symbol", nullable = false, updatable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, updatable = false, length = 4)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 8)
    private OrderType type;

    /** Null for MARKET orders, which have no price of their own. */
    @Column(name = "price", precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, updatable = false)
    private long quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private long remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Assigned by a database sequence on insert, so it is monotonic across all sessions.
     * Not written by the application; Hibernate reads it back after the insert.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "sequence_number", insertable = false, updatable = false)
    private Long sequenceNumber;

    protected Order() {
        // for JPA
    }

    private Order(UUID accountId, String symbol, Side side, OrderType type,
                  BigDecimal price, long quantity, Instant createdAt) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.OPEN;
        this.createdAt = createdAt;
    }

    public static Order limit(UUID accountId, String symbol, Side side, BigDecimal price,
                              long quantity, Instant createdAt) {
        Objects.requireNonNull(price, "LIMIT order requires a price");
        return new Order(accountId, symbol, side, OrderType.LIMIT, price, quantity, createdAt);
    }

    public static Order market(UUID accountId, String symbol, Side side,
                               long quantity, Instant createdAt) {
        return new Order(accountId, symbol, side, OrderType.MARKET, null, quantity, createdAt);
    }

    /** Quantity that has already traded. */
    public long filledQuantity() {
        return quantity - remainingQuantity;
    }

    /**
     * Reduce the remaining quantity by {@code qty} and move the status along.
     * Callers are responsible for never passing more than {@link #getRemainingQuantity()}.
     */
    public void fill(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive, was " + qty);
        }
        if (qty > remainingQuantity) {
            throw new IllegalArgumentException(
                    "cannot fill " + qty + " against remaining " + remainingQuantity);
        }
        remainingQuantity -= qty;
        status = remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    /** Take the order off the book. Only legal while it is still resting. */
    public void cancel() {
        if (status.isTerminal()) {
            throw new IllegalStateException("cannot cancel an order in status " + status);
        }
        status = OrderStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Order that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Order.class.hashCode();
    }

    @Override
    public String toString() {
        return "Order[" + id + " " + side + " " + quantity + " " + symbol
                + " @" + (price == null ? "MKT" : price)
                + " remaining=" + remainingQuantity + " " + status + "]";
    }
}
