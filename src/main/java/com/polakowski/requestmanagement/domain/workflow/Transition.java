package com.polakowski.requestmanagement.domain.workflow;

import java.util.Objects;

/**
 * A single edge of the state diagram: applying {@code action} while in {@code source} moves the
 * request to {@code target}.
 *
 * @param source                    state the request must be in for this transition to apply
 * @param action                    action triggering the transition
 * @param target                    state the request ends up in
 * @param reasonRequired            whether a business reason must be supplied by the caller
 * @param assignsPublicationNumber  whether reaching {@code target} assigns the unique publication
 *                                  number; expressed as a property of the edge rather than of a
 *                                  hard-coded state so the workflow stays data driven
 */
public record Transition(
        RequestState source,
        RequestAction action,
        RequestState target,
        boolean reasonRequired,
        boolean assignsPublicationNumber) {

    public Transition {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (source == target) {
            throw new IllegalArgumentException(
                    "Self transitions are not supported: %s -%s-> %s".formatted(source, action, target));
        }
    }

    public static Transition of(
            RequestState source,
            RequestAction action,
            RequestState target) {
        return new Transition(source, action, target, false, false);
    }

    public static Transition requiringReason(
            RequestState source,
            RequestAction action,
            RequestState target) {
        return new Transition(source, action, target, true, false);
    }

    public static Transition assigningPublicationNumber(
            RequestState source,
            RequestAction action,
            RequestState target) {
        return new Transition(source, action, target, false, true);
    }

    @Override
    public String toString() {
        return source + " -" + action + "-> " + target;
    }
}
