package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderType;
import com.harshshah.matchingengine.domain.Side;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether and how much an incoming order trades against a book. Pure: no Spring,
 * no database, no clock, and it mutates nothing it is given.
 *
 * <p><b>This class does not sort.</b> It is handed the resting orders already in priority
 * order and consumes them in the order given. Price-time priority is expressed once, in the
 * {@code order by} of the repository book queries, and this class trusts it. Sorting here as
 * well would mean two definitions of "best" that could drift apart, and the database is the
 * one that has to be right, because it is the one holding the rows.
 *
 * <p>Trusting the order is also what lets matching <b>stop at the first order that does not
 * cross</b> rather than scanning the whole book: if the best remaining price is too dear,
 * every price behind it is worse. That turns matching into a short walk down the front of
 * the book instead of a scan of every resting order in the symbol.
 *
 * <p>Every fill executes at the <b>resting</b> order's price. The resting order was there
 * first and its price is the one that was publicly advertised; filling at the incoming
 * order's price would let an aggressive buyer pay more than the seller asked, and would
 * make the price of a trade depend on which side happened to arrive second.
 */
public final class OrderBookMatcher {

    private OrderBookMatcher() {
        // static-only
    }

    /**
     * Match {@code incoming} against {@code restingBestFirst} and return the fills that
     * should result, in the order they should be applied.
     *
     * <p>Neither the incoming order nor any resting order is modified. The caller applies
     * the fills inside a transaction, so that a failure part-way through rolls the whole
     * submission back rather than leaving the book half-updated.
     *
     * @param restingBestFirst resting orders on the opposite side, best price first and
     *                         earliest arrival first within a price
     * @return the fills to apply, empty if nothing crosses
     */
    public static List<Fill> match(Order incoming, List<Order> restingBestFirst) {
        Objects.requireNonNull(incoming, "incoming order is required");
        Objects.requireNonNull(restingBestFirst, "resting orders are required");

        long remaining = incoming.getRemainingQuantity();
        if (remaining <= 0) {
            return List.of();
        }

        List<Fill> fills = new ArrayList<>();
        for (Order resting : restingBestFirst) {
            if (remaining == 0) {
                break;
            }
            requireMatchable(incoming, resting);
            if (!crosses(incoming, resting)) {
                // The book is best-first, so nothing behind this can cross either.
                break;
            }

            long quantity = Math.min(remaining, resting.getRemainingQuantity());
            if (quantity <= 0) {
                continue;
            }
            fills.add(new Fill(resting, quantity, resting.getPrice()));
            remaining -= quantity;
        }
        return List.copyOf(fills);
    }

    /**
     * Whether these two orders are willing to trade with each other.
     *
     * <p>A MARKET order has no price of its own, so it crosses whatever is there. A LIMIT
     * order crosses only at its price or better, and "better" runs in opposite directions
     * for the two sides: a buyer will pay up to its price, a seller will accept down to its.
     */
    static boolean crosses(Order incoming, Order resting) {
        if (incoming.getType() == OrderType.MARKET) {
            return true;
        }
        BigDecimal incomingPrice = incoming.getPrice();
        BigDecimal restingPrice = resting.getPrice();
        return incoming.getSide() == Side.BUY
                ? incomingPrice.compareTo(restingPrice) >= 0
                : incomingPrice.compareTo(restingPrice) <= 0;
    }

    /**
     * Guards against a caller handing over a book that is not actually this order's
     * opposite side. These are programming errors rather than user errors, so they fail
     * loudly here instead of quietly producing a wrong trade.
     */
    private static void requireMatchable(Order incoming, Order resting) {
        if (!incoming.getSymbol().equals(resting.getSymbol())) {
            throw new IllegalArgumentException("cannot match " + incoming.getSymbol()
                    + " against a book for " + resting.getSymbol());
        }
        if (resting.getSide() != incoming.getSide().opposite()) {
            throw new IllegalArgumentException("resting book must be the opposite side of "
                    + incoming.getSide() + ", found " + resting.getSide());
        }
        if (resting.getPrice() == null) {
            // A MARKET order never rests: it has no price at which it could sit.
            throw new IllegalArgumentException("a resting order must carry a price");
        }
    }
}
