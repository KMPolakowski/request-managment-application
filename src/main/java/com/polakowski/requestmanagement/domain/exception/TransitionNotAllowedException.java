package com.polakowski.requestmanagement.domain.exception;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Raised when an action is not a permitted transition out of the current state. */
@Getter
@Accessors(fluent = true)
public class TransitionNotAllowedException extends DomainException {

    private final RequestState currentState;
    private final RequestAction action;
    private final Set<RequestAction> allowedActions;

    public TransitionNotAllowedException(
            RequestState currentState,
            RequestAction action,
            Set<RequestAction> allowedActions) {
        super("Action %s is not allowed while the request is %s; allowed actions: %s".formatted(
                action,
                currentState,
                allowedActions.isEmpty() ? "none" : sorted(allowedActions)));
        this.currentState = currentState;
        this.action = action;
        this.allowedActions = Set.copyOf(allowedActions);
    }

    private static List<String> sorted(Set<RequestAction> actions) {
        return actions.stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }
}
