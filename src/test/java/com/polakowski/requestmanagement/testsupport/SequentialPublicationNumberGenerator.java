package com.polakowski.requestmanagement.testsupport;

import com.polakowski.requestmanagement.domain.port.PublicationNumberGenerator;
import java.util.concurrent.atomic.AtomicLong;

/** Predictable publication numbers, so that assertions can name the expected value. */
public class SequentialPublicationNumberGenerator implements PublicationNumberGenerator {

    private final AtomicLong counter;

    public SequentialPublicationNumberGenerator() {
        this(1);
    }

    public SequentialPublicationNumberGenerator(long firstNumber) {
        this.counter = new AtomicLong(firstNumber);
    }

    @Override
    public long next() {
        return counter.getAndIncrement();
    }

    public long issued() {
        return counter.get() - 1;
    }
}
