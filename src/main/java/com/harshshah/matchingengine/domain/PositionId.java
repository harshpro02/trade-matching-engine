package com.harshshah.matchingengine.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for {@link Position}: one position per account per symbol. */
public class PositionId implements Serializable {

    private UUID accountId;
    private String symbol;

    public PositionId() {
        // required by JPA
    }

    public PositionId(UUID accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PositionId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol);
    }

    @Override
    public String toString() {
        return accountId + "/" + symbol;
    }
}
