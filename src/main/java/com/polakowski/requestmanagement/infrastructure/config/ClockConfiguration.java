package com.polakowski.requestmanagement.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clock the application reads the time from.
 *
 * <p>Injecting it rather than calling {@code Instant.now()} keeps timestamps deterministic in tests
 * and audit entries consistent across a single transaction.
 */
@Configuration
public class ClockConfiguration {

    /** Microseconds, because that is the precision a timestamp column keeps once written. */
    private static final Duration DATABASE_PRECISION = Duration.ofNanos(1_000);

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), DATABASE_PRECISION);
    }
}
