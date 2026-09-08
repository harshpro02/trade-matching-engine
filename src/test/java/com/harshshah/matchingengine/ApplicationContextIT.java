package com.harshshah.matchingengine;

import com.harshshah.matchingengine.domain.Instrument;
import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.domain.Position;
import com.harshshah.matchingengine.domain.Side;
import com.harshshah.matchingengine.dto.OrderBookResponse;
import com.harshshah.matchingengine.repository.InstrumentRepository;
import com.harshshah.matchingengine.repository.OrderRepository;
import com.harshshah.matchingengine.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end scaffold check against a real Postgres: the context starts, Flyway builds the
 * schema, Hibernate agrees with it, the repositories round-trip, and the stub endpoint answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationContextIT {

    private static final String SYMBOL = "AAPL";

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void seedInstrument() {
        if (!instrumentRepository.existsById(SYMBOL)) {
            instrumentRepository.save(new Instrument(SYMBOL, Instant.now()));
        }
    }

    @Test
    void flywayHasAppliedTheInitialMigration() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).contains("instruments", "orders", "trades", "positions",
                "flyway_schema_history");
    }

    @Test
    void ordersRoundTripAndReceiveAMonotonicSequenceNumber() {
        UUID account = UUID.randomUUID();
        Order first = orderRepository.saveAndFlush(
                Order.limit(account, SYMBOL, Side.BUY, new BigDecimal("150.25"), 100, Instant.now()));
        Order second = orderRepository.saveAndFlush(
                Order.limit(account, SYMBOL, Side.BUY, new BigDecimal("150.25"), 50, Instant.now()));

        assertThat(first.getId()).isNotNull();
        assertThat(first.getSequenceNumber()).isNotNull();
        assertThat(second.getSequenceNumber()).isGreaterThan(first.getSequenceNumber());
        assertThat(orderRepository.findById(first.getId()))
                .get()
                .satisfies(loaded -> {
                    assertThat(loaded.getStatus()).isEqualTo(OrderStatus.OPEN);
                    assertThat(loaded.getPrice()).isEqualByComparingTo("150.25");
                    assertThat(loaded.getRemainingQuantity()).isEqualTo(100);
                });
    }

    @Test
    void restingBidsComeBackBestPriceFirstThenEarliestArrival() {
        UUID account = UUID.randomUUID();
        String symbol = "BOOKTEST";
        instrumentRepository.save(new Instrument(symbol, Instant.now()));

        Order cheap = orderRepository.saveAndFlush(
                Order.limit(account, symbol, Side.BUY, new BigDecimal("99.00"), 10, Instant.now()));
        Order dearEarly = orderRepository.saveAndFlush(
                Order.limit(account, symbol, Side.BUY, new BigDecimal("101.00"), 10, Instant.now()));
        Order dearLate = orderRepository.saveAndFlush(
                Order.limit(account, symbol, Side.BUY, new BigDecimal("101.00"), 10, Instant.now()));

        assertThat(orderRepository.findRestingBids(symbol))
                .extracting(Order::getId)
                .containsExactly(dearEarly.getId(), dearLate.getId(), cheap.getId());
    }

    @Test
    void positionsPersistTheirCompositeKeyAndMoneyScales() {
        UUID account = UUID.randomUUID();
        Position position = new Position(account, SYMBOL);
        position.applyFill(Side.BUY, 100, new BigDecimal("150.2500"));
        positionRepository.saveAndFlush(position);

        assertThat(positionRepository.findByAccountIdAndSymbol(account, SYMBOL))
                .get()
                .satisfies(loaded -> {
                    assertThat(loaded.getQuantity()).isEqualTo(100);
                    assertThat(loaded.getAverageCost()).isEqualByComparingTo("150.25");
                    assertThat(loaded.getAverageCost().scale()).isEqualTo(Position.COST_SCALE);
                    assertThat(loaded.getRealisedPnl()).isEqualByComparingTo("0");
                });
    }

    @Test
    void theBookEndpointAnswers() {
        ResponseEntity<OrderBookResponse> response =
                restTemplate.getForEntity("/api/book/{symbol}", OrderBookResponse.class, SYMBOL);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().symbol()).isEqualTo(SYMBOL);
    }

    @Test
    void actuatorReportsTheDatabaseIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
