package com.polakowski.requestmanagement.domain.exception;

/**
 * Base class for every business rule violation raised by the domain.
 *
 * <p>Domain exceptions carry no transport concern: translating them into HTTP responses is the
 * responsibility of the inbound adapter.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
