package com.polakowski.requestmanagement.testsupport;

import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Predictable identities for tests.
 *
 * <p>This is the payoff of having {@link RequestIdGenerator} as a port: a test can decide what the
 * next identifier will be, and a failure message reads
 * {@code 00000000-0000-0000-0000-000000000001} instead of a random UUID.
 */
public class SequentialRequestIdGenerator implements RequestIdGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public RequestId next() {
        return RequestId.of(new UUID(0L, counter.incrementAndGet()));
    }
}
