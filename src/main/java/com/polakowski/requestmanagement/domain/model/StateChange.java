package com.polakowski.requestmanagement.domain.model;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One entry of the audit trail: the request moved from {@code from} to {@code to}.
 *
 * <p>The very first entry of a request records its creation and therefore has no source state and
 * no action.
 *
 * @param sequenceNumber position in the history, starting at 1, so that entries recorded within
 *                       the same instant remain ordered
 * @param from           state left behind, absent for the creation entry
 * @param to             state entered
 * @param action         action that caused the change, absent for the creation entry
 * @param reason         business justification, present only for transitions that require one
 * @param occurredAt     when the change happened
 */
public record StateChange(
        int sequenceNumber,
        RequestState from,
        RequestState to,
        RequestAction action,
        String reason,
        Instant occurredAt) {

    public StateChange {
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive but was " + sequenceNumber);
        }
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    static StateChange creation(RequestState initialState, Instant occurredAt) {
        return new StateChange(1, null, initialState, null, null, occurredAt);
    }

    public Optional<RequestState> fromState() {
        return Optional.ofNullable(from);
    }

    public Optional<RequestAction> appliedAction() {
        return Optional.ofNullable(action);
    }

    public Optional<String> justification() {
        return Optional.ofNullable(reason);
    }
}
