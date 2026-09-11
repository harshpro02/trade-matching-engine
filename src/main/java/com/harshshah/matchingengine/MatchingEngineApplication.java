package com.harshshah.matchingengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class MatchingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineApplication.class, args);
    }

    /**
     * Injected rather than called statically so that time is a dependency like any other.
     * Trades are timestamped from this, and a test that needs a fixed instant can supply
     * one instead of trying to assert against whatever {@code Instant.now()} happened to
     * return mid-run.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
