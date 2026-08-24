package com.polakowski.requestmanagement.domain.exception;

import lombok.Getter;
import lombok.experimental.Accessors;

/** Raised when the name or the body of a request does not satisfy the domain constraints. */
@Getter
@Accessors(fluent = true)
public class InvalidRequestContentException extends DomainException {

    private final String field;

    public InvalidRequestContentException(String field, String message) {
        super(message);
        this.field = field;
    }
}
