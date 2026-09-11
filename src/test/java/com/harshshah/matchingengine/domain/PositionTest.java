package com.harshshah.matchingengine.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Position is where money is decided, so these cover every branch of applyFill: opening,
 * adding, reducing, flattening, and crossing through zero, in both directions.
 *
 * <p>The long sequence is also checked against plain cash in and out. That reconciliation
 * is the real test: an average cost that does not terminate, plus two separate roundings,
 * still has to reproduce the number a cashier would get counting notes.
 */
class PositionTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String SYMBOL = "AAPL";

    private static Position flatPosition() {
        return new Position(ACCOUNT, SYMBOL);
    }

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("opening")
    class Opening {

        @Test
        void aNewPositionIsFlatAndOwesNothing() {
            Position position = flatPosition();

            assertThat(position.isFlat()).isTrue();
            assertThat(position.getQuantity()).isZero();
            assertThat(position.getAverageCost()).isEqualByComparingTo("0");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("0");
        }

        @Test
        void buyingFromFlatOpensALongAtTheExecutionPriceAndRealisesNothing() {
            Position position = flatPosition();

            BigDecimal realised = position.applyFill(Side.BUY, 100, price("50.00"));

            assertThat(realised).isEqualByComparingTo("0");
            assertThat(position.getQuantity()).isEqualTo(100);
            assertThat(position.isLong()).isTrue();
            assertThat(position.getAverageCost()).isEqualByComparingTo("50.00");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("0");
        }

        @Test
        void sellingFromFlatOpensAShortAtTheExecutionPriceAndRealisesNothing() {
            Position position = flatPosition();

            BigDecimal realised = position.applyFill(Side.SELL, 200, price("48.00"));

            assertThat(realised).isEqualByComparingTo("0");
            assertThat(position.getQuantity()).isEqualTo(-200);
            assertThat(position.isShort()).isTrue();
            assertThat(position.getAverageCost()).isEqualByComparingTo("48.00");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("adding to a position")
    class Adding {

        @Test
        void addingToALongReweightsAverageCostAndRealisesNothing() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));

            BigDecimal realised = position.applyFill(Side.BUY, 50, price("61.00"));

            // (100 x 50.00 + 50 x 61.00) / 150 = 8050 / 150 = 53.6666...
            assertThat(realised).isEqualByComparingTo("0");
            assertThat(position.getQuantity()).isEqualTo(150);
            assertThat(position.getAverageCost()).isEqualByComparingTo("53.66666667");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("0");
        }

        @Test
        void addingToAShortReweightsAverageCostAndRealisesNothing() {
            Position position = flatPosition();
            position.applyFill(Side.SELL, 200, price("48.00"));

            BigDecimal realised = position.applyFill(Side.SELL, 100, price("45.00"));

            // (200 x 48.00 + 100 x 45.00) / 300 = 14100 / 300 = 47.00
            assertThat(realised).isEqualByComparingTo("0");
            assertThat(position.getQuantity()).isEqualTo(-300);
            assertThat(position.getAverageCost()).isEqualByComparingTo("47.00");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("0");
        }

        @Test
        void averageCostIsCarriedAtEightPlacesBecauseItIsAQuotient() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));

            position.applyFill(Side.BUY, 50, price("61.00"));

            // Four places would give 53.6667, overstating cost by 0.00003333 per unit,
            // which is then multiplied by every unit later closed against it.
            assertThat(position.getAverageCost().scale()).isEqualTo(Position.COST_SCALE);
            assertThat(position.getAverageCost()).isEqualByComparingTo("53.66666667");
        }
    }

    @Nested
    @DisplayName("reducing a position")
    class Reducing {

        @Test
        void partiallyClosingALongRealisesAgainstTheOldCostBasisAndLeavesItAlone() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));
            position.applyFill(Side.BUY, 50, price("61.00"));

            BigDecimal realised = position.applyFill(Side.SELL, 40, price("58.00"));

            // (58.00 - 53.66666667) x 40 = 173.3333332 -> 173.3333
            assertThat(realised).isEqualByComparingTo("173.3333");
            assertThat(position.getQuantity()).isEqualTo(110);
            assertThat(position.getAverageCost()).isEqualByComparingTo("53.66666667");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("173.3333");
        }

        @Test
        void partiallyClosingAShortProfitsWhenBoughtBackCheaper() {
            Position position = flatPosition();
            position.applyFill(Side.SELL, 200, price("48.00"));
            position.applyFill(Side.SELL, 100, price("45.00"));

            BigDecimal realised = position.applyFill(Side.BUY, 120, price("44.00"));

            // (47.00 - 44.00) x 120 = 360.0000. Sold high, bought back low, so this is a
            // profit even though the execution price sits below the cost basis.
            assertThat(realised).isEqualByComparingTo("360.0000");
            assertThat(realised.signum()).isPositive();
            assertThat(position.getQuantity()).isEqualTo(-180);
            assertThat(position.getAverageCost()).isEqualByComparingTo("47.00");
        }

        @Test
        void closingAShortDearerThanItWasOpenedIsALoss() {
            Position position = flatPosition();
            position.applyFill(Side.SELL, 100, price("47.00"));

            BigDecimal realised = position.applyFill(Side.BUY, 100, price("50.00"));

            assertThat(realised).isEqualByComparingTo("-300.0000");
            assertThat(realised.signum()).isNegative();
            assertThat(position.isFlat()).isTrue();
        }
    }

    @Nested
    @DisplayName("closing out completely")
    class Closing {

        @Test
        void fullyClosingALongFlattensThePositionAndClearsTheCostBasis() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));
            position.applyFill(Side.BUY, 50, price("61.00"));
            position.applyFill(Side.SELL, 40, price("58.00"));

            BigDecimal realised = position.applyFill(Side.SELL, 110, price("55.00"));

            // (55.00 - 53.66666667) x 110 = 146.6666663 -> 146.6667
            assertThat(realised).isEqualByComparingTo("146.6667");
            assertThat(position.isFlat()).isTrue();
            assertThat(position.getQuantity()).isZero();
            assertThat(position.getAverageCost()).isEqualByComparingTo("0");
        }

        @Test
        void aRoundTripReconcilesExactlyWithPlainCashInAndOut() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));
            position.applyFill(Side.BUY, 50, price("61.00"));
            position.applyFill(Side.SELL, 40, price("58.00"));
            position.applyFill(Side.SELL, 110, price("55.00"));

            // Cash out (100 x 50 + 50 x 61) = 8050. Cash in (40 x 58 + 110 x 55) = 8370.
            // A cashier counting notes makes that 320.00, and so must we, despite an
            // average cost that does not terminate and two roundings along the way.
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("320.0000");
            assertThat(position.isFlat()).isTrue();
        }
    }

    @Nested
    @DisplayName("crossing through zero")
    class CrossingThroughZero {

        @Test
        void sellingMoreThanHeldClosesTheLongAndOpensAShortAtTheExecutionPrice() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));

            BigDecimal realised = position.applyFill(Side.SELL, 150, price("55.00"));

            // Realises on the 100 held, then opens a NEW short 50 at 55.00, not at the
            // closed long 50.00 basis, which was fully consumed computing the 500 above.
            assertThat(realised).isEqualByComparingTo("500.0000");
            assertThat(position.getQuantity()).isEqualTo(-50);
            assertThat(position.isShort()).isTrue();
            assertThat(position.getAverageCost()).isEqualByComparingTo("55.00");
        }

        @Test
        void theShortOpenedByCrossingCarriesTheExecutionPriceSoItsOwnPnlIsRight() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));
            position.applyFill(Side.SELL, 150, price("55.00"));

            BigDecimal realised = position.applyFill(Side.BUY, 50, price("50.00"));

            // Sold 50 at 55.00 and bought them back at 50.00: 250 of real profit. Carrying
            // the old long 50.00 basis into the short would have reported zero here.
            assertThat(realised).isEqualByComparingTo("250.0000");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("750.0000");
            assertThat(position.isFlat()).isTrue();
        }

        @Test
        void buyingMoreThanHeldClosesTheShortAndOpensALongAtTheExecutionPrice() {
            Position position = flatPosition();
            position.applyFill(Side.SELL, 200, price("48.00"));
            position.applyFill(Side.SELL, 100, price("45.00"));
            position.applyFill(Side.BUY, 120, price("44.00"));

            BigDecimal realised = position.applyFill(Side.BUY, 300, price("50.00"));

            // Closes the remaining 180 at a loss, then opens a long 120 at 50.00.
            assertThat(realised).isEqualByComparingTo("-540.0000");
            assertThat(position.getQuantity()).isEqualTo(120);
            assertThat(position.isLong()).isTrue();
            assertThat(position.getAverageCost()).isEqualByComparingTo("50.00");
            assertThat(position.getRealisedPnl()).isEqualByComparingTo("-180.0000");
        }

        @Test
        void crossingExactlyToFlatDoesNotOpenAnythingNew() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));

            BigDecimal realised = position.applyFill(Side.SELL, 100, price("55.00"));

            assertThat(realised).isEqualByComparingTo("500.0000");
            assertThat(position.isFlat()).isTrue();
            assertThat(position.isLong()).isFalse();
            assertThat(position.isShort()).isFalse();
            assertThat(position.getAverageCost()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("scales and guards")
    class ScalesAndGuards {

        @Test
        void realisedPnlIsCarriedAtFourPlaces() {
            Position position = flatPosition();
            position.applyFill(Side.BUY, 100, price("50.00"));
            position.applyFill(Side.BUY, 50, price("61.00"));

            position.applyFill(Side.SELL, 40, price("58.00"));

            assertThat(position.getRealisedPnl().scale()).isEqualTo(Position.PNL_SCALE);
        }

        @Test
        void aNonPositiveFillQuantityIsRejected() {
            Position position = flatPosition();

            assertThatThrownBy(() -> position.applyFill(Side.BUY, 0, price("50.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fill quantity must be positive");

            assertThatThrownBy(() -> position.applyFill(Side.BUY, -5, price("50.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fill quantity must be positive");
        }
    }
}
