package com.polakowski.requestmanagement.domain.exception;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import java.util.Locale;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Raised when a transition that must be justified is attempted without a reason. */
@Getter
@Accessors(fluent = true)
public class ReasonRequiredException extends DomainException {

    private final RequestAction action;

    public ReasonRequiredException(RequestAction action) {
        super("A reason must be supplied in order to %s a request".formatted(
                action.name().toLowerCase(Locale.ROOT)));
        this.action = action;
    }
}
