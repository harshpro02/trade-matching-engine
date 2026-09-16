package com.harshshah.matchingengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "positions")
@IdClass(PositionId.class)
public class Position {
    public static final int COST_SCALE = 8;

    public static final int PNL_SCALE = 4;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Id
    @Column(name = "symbol", nullable = false, updatable = false, length = 32)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "average_cost", nullable = false, precision = 19, scale = COST_SCALE)
    private BigDecimal averageCost;

    @Column(name = "realised_pnl", nullable = false, precision = 19, scale = PNL_SCALE)
    private BigDecimal realisedPnl;

    protected Position() {
    }

    public Position(UUID accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = 0L;
        this.averageCost = BigDecimal.ZERO.setScale(COST_SCALE, ROUNDING);
        this.realisedPnl = BigDecimal.ZERO.setScale(PNL_SCALE, ROUNDING);
    }

    public BigDecimal applyFill(Side side, long fillQuantity, BigDecimal executionPrice) {
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive, was " + fillQuantity);
        }
        if (executionPrice == null || executionPrice.signum() <= 0) {
            throw new IllegalArgumentException("execution price must be positive, was " + executionPrice);
        }
        long signedFill = side == Side.BUY ? fillQuantity : -fillQuantity;
        BigDecimal realisedDelta = BigDecimal.ZERO.setScale(PNL_SCALE, ROUNDING);

        if (quantity == 0) {
            quantity = signedFill;
            averageCost = executionPrice.setScale(COST_SCALE, ROUNDING);
            return realisedDelta;
        }

        if (isSameDirection(quantity, signedFill)) {
            long newQuantity = quantity + signedFill;
            BigDecimal heldNotional = averageCost.multiply(BigDecimal.valueOf(Math.abs(quantity)));
            BigDecimal addedNotional = executionPrice.multiply(BigDecimal.valueOf(fillQuantity));
            averageCost = heldNotional.add(addedNotional)
                    .divide(BigDecimal.valueOf(Math.abs(newQuantity)), COST_SCALE, ROUNDING);
            quantity = newQuantity;
            return realisedDelta;
        }

        long closedQuantity = Math.min(Math.abs(quantity), fillQuantity);
        BigDecimal profitPerUnit = quantity > 0
                ? executionPrice.subtract(averageCost)
                : averageCost.subtract(executionPrice);
        realisedDelta = profitPerUnit
                .multiply(BigDecimal.valueOf(closedQuantity))
                .setScale(PNL_SCALE, ROUNDING);
        realisedPnl = realisedPnl.add(realisedDelta);

        long newQuantity = quantity + signedFill;
        if (newQuantity == 0) {
            averageCost = BigDecimal.ZERO.setScale(COST_SCALE, ROUNDING);
        } else if (!isSameDirection(newQuantity, quantity)) {
            // Crossed through zero: this is a new position in the other direction, so it
            // takes the execution price. The old basis was consumed by the realisation above.
            averageCost = executionPrice.setScale(COST_SCALE, ROUNDING);
        }

        quantity = newQuantity;
        return realisedDelta;
    }

    private static boolean isSameDirection(long a, long b) {
        return (a > 0 && b > 0) || (a < 0 && b < 0);
    }

    public boolean isFlat() {
        return quantity == 0;
    }

    public boolean isLong() {
        return quantity > 0;
    }

    public boolean isShort() {
        return quantity < 0;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getRealisedPnl() {
        return realisedPnl;
    }

    @Override
    public String toString() {
        return "Position[" + accountId + " " + symbol + " qty=" + quantity
                + " avg=" + averageCost + " realised=" + realisedPnl + "]";
    }
}
