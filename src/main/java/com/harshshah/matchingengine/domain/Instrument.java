package com.harshshah.matchingengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "instruments")
public class Instrument {
    @Id
    @Column(name = "symbol", nullable = false, updatable = false, length = 32)
    private String symbol;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Instrument() {
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
