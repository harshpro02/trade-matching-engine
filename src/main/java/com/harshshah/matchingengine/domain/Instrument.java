package com.harshshah.matchingengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per tradable symbol. This table exists for exactly one reason: it gives the
 * matching engine a single row per symbol to take a {@code SELECT ... FOR UPDATE} on,
 * which serialises all matching for that symbol while leaving different symbols free to
 * match in parallel.
 *
 * <p>Locking the book rather than the individual resting orders avoids the deadlock you
 * get when two sessions lock overlapping sets of orders in different orders, and it avoids
 * the subtler problem that {@code ORDER BY ... LIMIT n FOR UPDATE} re-evaluates rows after
 * the lock is granted, so two sessions can end up with different views of "the best n".
 */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @Column(name = "symbol", nullable = false, updatable = false, length = 32)
    private String symbol;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Instrument() {
        // for JPA
    }

    public Instrument(String symbol, Instant createdAt) {
        this.symbol = symbol;
        this.createdAt = createdAt;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
