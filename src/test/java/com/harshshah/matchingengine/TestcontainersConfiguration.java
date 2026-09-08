package com.harshshah.matchingengine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway Postgres for integration tests.
 *
 * <p>The engine relies on Postgres-specific behaviour - {@code SELECT ... FOR UPDATE} row
 * locking, {@code NUMERIC} arithmetic, identity columns, partial indexes - so testing it
 * against an in-memory substitute would be testing something other than what ships.
 * {@code @ServiceConnection} points the application's datasource at the container, so no
 * JDBC URL has to be duplicated in test config.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("matching_engine")
                .withUsername("matching")
                .withPassword("matching")
                .withReuse(true);
    }
}
