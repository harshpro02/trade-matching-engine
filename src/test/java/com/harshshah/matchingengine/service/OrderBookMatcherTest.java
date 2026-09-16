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
            partlyFilled.fill(70);

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
            assertThat(fills.get(2).quantity()).isEqualTo(50);
        }

        @Test
        void anIncomingOrderLargerThanTheBookTakesEverythingThatCrosses() {
            List<Fill> fills = OrderBookMatcher.match(
                    buy("152.00", 500),
                    List.of(sell("150.00", 100), sell("151.00", 100)));

            assertThat(fills).hasSize(2);
            assertThat(fills.stream().mapToLong(Fill::quantity).sum()).isEqualTo(200);
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

            assertThat(incoming.getRemainingQuantity()).isEqualTo(100);
            assertThat(resting.getRemainingQuantity()).isEqualTo(100);
        }

        @Test
        void theBookIsConsumedInTheOrderGivenRatherThanBeingResorted() {
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
