package com.harshshah.matchingengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * What one account holds in one symbol, and how much money it has actually made there.
 *
 * <p>{@code quantity} is signed: positive is long, negative is short, zero is flat.
 *
 * <p><b>Realised P&amp;L accrues only when a position is reduced.</b> Opening or adding to a
 * position moves cash but does not create profit, it just changes what you own and at what
 * average cost. Profit is only a fact once you have closed out at a known price; until then
 * it is a mark-to-market opinion that changes every time the market moves. So this class
 * realises {@code (executionPrice - averageCost) * closedQuantity} for a long, inverted for
 * a short, and leaves {@code averageCost} alone when a position is merely reduced.
 */
@Entity
@Table(name = "positions")
@IdClass(PositionId.class)
public class Position {

    /**
     * Average cost is a quotient, so it needs more precision than a price does. Carrying it
     * at 4dp would leak a fraction of a cent into realised P&amp;L on every partial fill.
     */
    public static final int COST_SCALE = 8;

    /** Money that has actually been made or lost, rounded the way money is rounded. */
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
        // for JPA
    }

    public Position(UUID accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = 0L;
        this.averageCost = BigDecimal.ZERO.setScale(COST_SCALE, ROUNDING);
        this.realisedPnl = BigDecimal.ZERO.setScale(PNL_SCALE, ROUNDING);
    }

    /**
     * Apply one side of one fill to this position and return the realised P&amp;L it produced.
     *
     * <p>Four things can happen:
     * <ol>
     *   <li>flat to open - average cost becomes the execution price, nothing realised</li>
     *   <li>increase - average cost is re-weighted, nothing realised</li>
     *   <li>reduce or close - realises against the old average cost, which does not move</li>
     *   <li>cross through zero - realises the whole old position, then opens the remainder
     *       in the other direction at the execution price</li>
     * </ol>
     * The fourth case is the one that is easy to miss: selling 150 when you are long 100
     * realises on 100 and leaves you short 50, not short 50 at the old long's cost basis.
     *
     * @return the increment added to realised P&amp;L, zero if this fill only opened or added
     */
    public BigDecimal applyFill(Side side, long fillQuantity, BigDecimal executionPrice) {
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive, was " + fillQuantity);
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

        // Opposite direction: this fill closes some or all of what is held.
        long closedQuantity = Math.min(Math.abs(quantity), fillQuantity);
        BigDecimal profitPerUnit = quantity > 0
                ? executionPrice.subtract(averageCost)   // long: sold above cost is profit
                : averageCost.subtract(executionPrice);  // short: bought below cost is profit
        realisedDelta = profitPerUnit
                .multiply(BigDecimal.valueOf(closedQuantity))
                .setScale(PNL_SCALE, ROUNDING);
        realisedPnl = realisedPnl.add(realisedDelta);

        long newQuantity = quantity + signedFill;
        if (newQuantity == 0) {
            averageCost = BigDecimal.ZERO.setScale(COST_SCALE, ROUNDING);
        } else if (!isSameDirection(newQuantity, quantity)) {
            averageCost = executionPrice.setScale(COST_SCALE, ROUNDING);
        }
        // else: reduced but not closed, the surviving lot keeps its original cost basis
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
