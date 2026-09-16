package com.harshshah.matchingengine.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String SYMBOL = "AAPL";
    private static final Instant NOW = Instant.parse("2026-09-08T14:30:00Z");

    private static Order buyLimit(long quantity) {
        return Order.limit(ACCOUNT, SYMBOL, Side.BUY, new BigDecimal("150.25"), quantity, NOW);
    }

    @Nested
    @DisplayName("creation")
    class Creation {
        @Test
        void limitOrderStartsOpenAndFullyUnfilled() {
            Order order = buyLimit(100);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(order.getQuantity()).isEqualTo(100);
            assertThat(order.getRemainingQuantity()).isEqualTo(100);
            assertThat(order.filledQuantity()).isZero();
            assertThat(order.getType()).isEqualTo(OrderType.LIMIT);
            assertThat(order.getPrice()).isEqualByComparingTo("150.25");
        }

        @Test
        void marketOrderCarriesNoPrice() {
            Order order = Order.market(ACCOUNT, SYMBOL, Side.SELL, 50, NOW);

            assertThat(order.getType()).isEqualTo(OrderType.MARKET);
            assertThat(order.getPrice()).isNull();
        }

        @Test
        void limitOrderWithoutPriceIsRejected() {
            assertThatThrownBy(() -> Order.limit(ACCOUNT, SYMBOL, Side.BUY, null, 100, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("LIMIT order requires a price");
        }
    }

    @Nested
    @DisplayName("filling")
    class Filling {
        @Test
        void partialFillLeavesTheOrderResting() {
            Order order = buyLimit(100);

            order.fill(40);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.getStatus().isResting()).isTrue();
            assertThat(order.getRemainingQuantity()).isEqualTo(60);
            assertThat(order.filledQuantity()).isEqualTo(40);
        }

        @Test
        void fillingTheRemainderCompletesTheOrder() {
            Order order = buyLimit(100);

            order.fill(40);
            order.fill(60);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getStatus().isTerminal()).isTrue();
            assertThat(order.getRemainingQuantity()).isZero();
        }

        @Test
        void fillingMoreThanRemainsIsRejected() {
            Order order = buyLimit(100);
            order.fill(90);

            assertThatThrownBy(() -> order.fill(20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot fill 20 against remaining 10");
        }

        @Test
        void fillingANonPositiveQuantityIsRejected() {
            Order order = buyLimit(100);

            assertThatThrownBy(() -> order.fill(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("cancelling")
    class Cancelling {
        @Test
        void anOpenOrderCanBeCancelled() {
            Order order = buyLimit(100);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatus().isResting()).isFalse();
        }

        @Test
        void aPartiallyFilledOrderCanBeCancelled() {
            Order order = buyLimit(100);
            order.fill(40);

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.filledQuantity()).isEqualTo(40);
        }

        @Test
        void aFilledOrderCannotBeCancelled() {
            Order order = buyLimit(100);
            order.fill(100);

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot cancel an order in status FILLED");
        }

        @Test
        void anAlreadyCancelledOrderCannotBeCancelledAgain() {
            Order order = buyLimit(100);
            order.cancel();

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void sidesAreOpposites() {
        assertThat(Side.BUY.opposite()).isEqualTo(Side.SELL);
        assertThat(Side.SELL.opposite()).isEqualTo(Side.BUY);
    }
}
