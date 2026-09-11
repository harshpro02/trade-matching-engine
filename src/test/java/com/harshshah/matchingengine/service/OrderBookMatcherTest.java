package com.harshshah.matchingengine.service;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The matcher is a pure decision function, so these tests need no Spring context and no
 * database and run in milliseconds.
 *
 * <p>Note what is deliberately absent: there is no test that the book comes back in price
 * order. The matcher does not sort, it consumes the list it is given, and the ordering is
 * the repository query's job. {@code ApplicationContextIT} covers that against real Postgres,
 * which is the only place it can honestly be tested.
 */
class OrderBookMatcherTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String SYMBOL = "AAPL";
    private static final Instant NOW = Instant.parse("2026-09-11T14:30:00Z");

    private static Order buy(String price, long quantity) {
        return Order.limit(ACCOUNT, SYMBOL, Side.BUY, new BigDecimal(price), quantity, NOW);
    }

    private static Order sell(String price, long quantity) {
        return Order.limit(ACCOUNT, SYMBOL, Side.SELL, new BigDecimal(price), quantity, NOW);
    }

    @Nested
    @DisplayName("whether two orders cross")
    class Crossing {

        @Test
        void anEmptyBookProducesNoFills() {
            List<Fill> fills = OrderBookMatcher.match(buy("150.00", 100), List.of());

            assertThat(fills).isEmpty();
        }

        @Test
        void aBuyBelowTheBestAskDoesNotCross() {
            List<Fill> fills = OrderBookMatcher.match(buy("149.99", 100), List.of(sell("150.00", 100)));

            assertThat(fills).isEmpty();
        }

        @Test
        void aBuyAtExactlyTheAskCrosses() {
            List<Fill> fills = OrderBookMatcher.match(buy("150.00", 100), List.of(sell("150.00", 100)));

            assertThat(fills).hasSize(1);
            assertThat(fills.getFirst().quantity()).isEqualTo(100);
        }

        @Test
        void aBuyAboveTheAskCrosses() {
            List<Fill> fills = OrderBookMatcher.match(buy("151.00", 100), List.of(sell("150.00", 100)));

            assertThat(fills).hasSize(1);
        }

        @Test
        void aSellAboveTheBestBidDoesNotCross() {
            List<Fill> fills = OrderBookMatcher.match(sell("150.01", 100), List.of(buy("150.00", 100)));

            assertThat(fills).isEmpty();
        }

        @Test
        void aSellAtOrBelowTheBidCrosses() {
            assertThat(OrderBookMatcher.match(sell("150.00", 100), List.of(buy("150.00", 100)))).hasSize(1);
            assertThat(OrderBookMatcher.match(sell("149.00", 100), List.of(buy("150.00", 100)))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("execution price")
    class ExecutionPrice {

        @Test
        void theFillExecutesAtTheRestingPriceNotTheIncomingPrice() {
            // Buyer is willing to pay 155.00, but the seller advertised 150.00 and was
            // there first, so the trade happens at 150.00 and the buyer saves the spread.
            List<Fill> fills = OrderBookMatcher.match(buy("155.00", 100), List.of(sell("150.00", 100)));

            assertThat(fills.getFirst().price()).isEqualByComparingTo("150.00");
        }

        @Test
        void eachLevelExecutesAtItsOwnRestingPrice() {
            List<Fill> fills = OrderBookMatcher.match(
                    buy("152.00", 150),
                    List.of(sell("150.00", 100), sell("151.00", 100)));

            assertThat(fills).hasSize(2);
            assertThat(fills.get(0).price()).isEqualByComparingTo("150.00");
            assertThat(fills.get(1).price()).isEqualByComparingTo("151.00");
        }
    }

    @Nested
    @DisplayName("how much trades")
    class Quantity {

        @Test
        void aSmallIncomingOrderPartiallyFillsOneRestingOrder() {
            List<Fill> fills = OrderBookMatcher.match(buy("150.00", 40), List.of(sell("150.00", 100)));

            assertThat(fills).hasSize(1);
            assertThat(fills.getFirst().quantity()).isEqualTo(40);
        }

        @Test
        void theFillIsCappedByWhatTheRestingOrderHasLeft() {
            Order partlyFilled = sell("150.00", 100);
            partlyFilled.fill(70); // only 30 left on the book

            List<Fill> fills = OrderBookMatcher.match(buy("150.00", 100), List.of(partlyFilled));

            assertThat(fills).hasSize(1);
            assertThat(fills.getFirst().quantity()).isEqualTo(30);
        }

        @Test
        void anIncomingOrderWalksSuccessiveLevelsUntilItIsFilled() {
            List<Fill> fills = OrderBookMatcher.match(
                    buy("152.00", 250),
                    List.of(sell("150.00", 100), sell("151.00", 100), sell("152.00", 100)));

            assertThat(fills).hasSize(3);
            assertThat(fills.get(0).quantity()).isEqualTo(100);
            assertThat(fills.get(1).quantity()).isEqualTo(100);
            assertThat(fills.get(2).quantity()).isEqualTo(50); // stops when filled
        }

        @Test
        void anIncomingOrderLargerThanTheBookTakesEverythingThatCrosses() {
            List<Fill> fills = OrderBookMatcher.match(
                    buy("152.00", 500),
                    List.of(sell("150.00", 100), sell("151.00", 100)));

            assertThat(fills).hasSize(2);
            assertThat(fills.stream().mapToLong(Fill::quantity).sum()).isEqualTo(200);
            // The remaining 300 is the caller's problem: it rests, or is cancelled if MARKET.
        }
    }

    @Nested
    @DisplayName("stopping early")
    class StoppingEarly {

        @Test
        void matchingStopsAtTheFirstLevelThatDoesNotCross() {
            List<Fill> fills = OrderBookMatcher.match(
                    buy("150.50", 300),
                    List.of(sell("150.00", 100), sell("151.00", 100), sell("152.00", 100)));

            // 151.00 is too dear, and because the book is best-first everything behind it
            // is dearer still, so there is no reason to look any further.
            assertThat(fills).hasSize(1);
            assertThat(fills.getFirst().price()).isEqualByComparingTo("150.00");
        }

        @Test
        void anOrderWithNothingLeftToFillProducesNoFills() {
            Order exhausted = buy("150.00", 100);
            exhausted.fill(100);

            List<Fill> fills = OrderBookMatcher.match(exhausted, List.of(sell("150.00", 100)));

            assertThat(fills).isEmpty();
        }
    }

    @Nested
    @DisplayName("market orders")
    class MarketOrders {

        @Test
        void aMarketBuyCrossesEveryLevelHoweverDearBecauseItHasNoPriceOfItsOwn() {
            Order incoming = Order.market(ACCOUNT, SYMBOL, Side.BUY, 250, NOW);

            List<Fill> fills = OrderBookMatcher.match(
                    incoming,
                    List.of(sell("150.00", 100), sell("400.00", 100), sell("999.00", 100)));

            assertThat(fills).hasSize(3);
            assertThat(fills.get(2).price()).isEqualByComparingTo("999.00");
        }

        @Test
        void aMarketSellCrossesEveryLevelHoweverCheap() {
            Order incoming = Order.market(ACCOUNT, SYMBOL, Side.SELL, 150, NOW);

            List<Fill> fills = OrderBookMatcher.match(
                    incoming,
                    List.of(buy("150.00", 100), buy("1.00", 100)));

            assertThat(fills).hasSize(2);
            assertThat(fills.get(1).price()).isEqualByComparingTo("1.00");
        }

        @Test
        void aMarketOrderStillStopsWhenTheBookRunsOut() {
            Order incoming = Order.market(ACCOUNT, SYMBOL, Side.BUY, 500, NOW);

            List<Fill> fills = OrderBookMatcher.match(incoming, List.of(sell("150.00", 100)));

            assertThat(fills).hasSize(1);
            assertThat(fills.getFirst().quantity()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("purity and preconditions")
    class PurityAndPreconditions {

        @Test
        void matchingMutatesNeitherTheIncomingOrderNorTheBook() {
            Order incoming = buy("155.00", 100);
            Order resting = sell("150.00", 100);

            OrderBookMatcher.match(incoming, List.of(resting));

            // Deciding is not doing. The service applies these inside a transaction.
            assertThat(incoming.getRemainingQuantity()).isEqualTo(100);
            assertThat(resting.getRemainingQuantity()).isEqualTo(100);
        }

        @Test
        void theBookIsConsumedInTheOrderGivenRatherThanBeingResorted() {
            // Deliberately out of price order. The matcher must not "fix" it: if the query
            // ever returned a wrongly ordered book, silently re-sorting here would hide it.
            List<Fill> fills = OrderBookMatcher.match(
                    buy("152.00", 150),
                    List.of(sell("151.00", 100), sell("150.00", 100)));

            assertThat(fills.get(0).price()).isEqualByComparingTo("151.00");
            assertThat(fills.get(1).price()).isEqualByComparingTo("150.00");
        }

        @Test
        void aBookOnTheSameSideAsTheIncomingOrderIsRejected() {
            assertThatThrownBy(() -> OrderBookMatcher.match(buy("150.00", 100), List.of(buy("150.00", 100))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("opposite side");
        }

        @Test
        void aBookForADifferentSymbolIsRejected() {
            Order otherSymbol = Order.limit(ACCOUNT, "MSFT", Side.SELL, new BigDecimal("150.00"), 100, NOW);

            assertThatThrownBy(() -> OrderBookMatcher.match(buy("150.00", 100), List.of(otherSymbol)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MSFT");
        }
    }
}
