package com.polakowski.requestmanagement.domain.workflow;

import static com.polakowski.requestmanagement.domain.workflow.RequestAction.ACCEPT;
import static com.polakowski.requestmanagement.domain.workflow.RequestAction.DELETE;
import static com.polakowski.requestmanagement.domain.workflow.RequestAction.PUBLISH;
import static com.polakowski.requestmanagement.domain.workflow.RequestAction.REJECT;
import static com.polakowski.requestmanagement.domain.workflow.RequestAction.VERIFY;
import static com.polakowski.requestmanagement.domain.workflow.RequestState.ACCEPTED;
import static com.polakowski.requestmanagement.domain.workflow.RequestState.CREATED;
import static com.polakowski.requestmanagement.domain.workflow.RequestState.DELETED;
import static com.polakowski.requestmanagement.domain.workflow.RequestState.PUBLISHED;
import static com.polakowski.requestmanagement.domain.workflow.RequestState.REJECTED;
import static com.polakowski.requestmanagement.domain.workflow.RequestState.VERIFIED;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The declarative description of a request lifecycle: where a request starts, which transitions
 * exist, and in which states its body may still be edited.
 *
 * <p>This is plain data. It is validated on construction and can be assembled from any source -
 * the application assembles it from external configuration - which keeps the lifecycle a
 * configuration concern instead of a hard-coded chain of {@code if} statements.
 *
 * @param initialState          state a freshly created request starts in
 * @param transitions           permitted edges of the state diagram
 * @param contentEditableStates states in which the body of a request may still be modified
 */
public record WorkflowDefinition(
        RequestState initialState,
        Set<Transition> transitions,
        Set<RequestState> contentEditableStates) {

    public WorkflowDefinition {
        Objects.requireNonNull(initialState, "initialState must not be null");
        Objects.requireNonNull(transitions, "transitions must not be null");
        Objects.requireNonNull(contentEditableStates, "contentEditableStates must not be null");
        if (transitions.isEmpty()) {
            throw new IllegalArgumentException("A workflow must define at least one transition");
        }
        transitions = Set.copyOf(transitions);
        contentEditableStates = Set.copyOf(contentEditableStates);
        rejectAmbiguousTransitions(transitions);
    }

    /**
     * The workflow drawn in the state diagram of the specification. It is used whenever no
     * lifecycle is supplied through configuration.
     */
    public static WorkflowDefinition fromStateDiagram() {
        List<Transition> edges = List.of(
                Transition.of(CREATED, VERIFY, VERIFIED),
                Transition.requiringReason(CREATED, DELETE, DELETED),
                Transition.of(VERIFIED, ACCEPT, ACCEPTED),
                Transition.requiringReason(VERIFIED, REJECT, REJECTED),
                Transition.requiringReason(ACCEPTED, REJECT, REJECTED),
                Transition.assigningPublicationNumber(ACCEPTED, PUBLISH, PUBLISHED));

        return new WorkflowDefinition(
                CREATED,
                Set.copyOf(edges),
                Set.of(CREATED, VERIFIED));
    }

    private static void rejectAmbiguousTransitions(Set<Transition> transitions) {
        Set<String> seen = new HashSet<>();
        for (Transition transition : transitions) {
            String key = transition.source() + "/" + transition.action();

            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Ambiguous workflow: %s is defined more than once for state %s".formatted(
                                transition.action(),
                                transition.source()));
            }
        }
    }
}
