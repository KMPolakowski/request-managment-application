package com.polakowski.requestmanagement.domain.exception;

import com.polakowski.requestmanagement.domain.model.RequestId;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Raised when an operation targets a request that does not exist. */
@Getter
@Accessors(fluent = true)
public class RequestNotFoundException extends DomainException {

    private final RequestId requestId;

    public RequestNotFoundException(RequestId requestId) {
        super("Request %s does not exist".formatted(requestId.value()));
        this.requestId = requestId;
    }
}
