package com.polakowski.requestmanagement.infrastructure.config;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.Transition;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External description of the request lifecycle.
 *
 * <p>Binding the state diagram to configuration is what makes the workflow a deployment decision:
 * adding a state, an action or a mandatory reason does not require touching the domain. When no
 * lifecycle is configured, the diagram from the specification is used.
 *
 * @param initialState          state a new request starts in
 * @param contentEditableStates states in which the body may still be modified
 * @param transitions           edges of the state diagram
 */
@ConfigurationProperties(prefix = "request-management.workflow")
public record WorkflowProperties(
        RequestState initialState,
        List<RequestState> contentEditableStates,
        List<TransitionProperties> transitions) {

    /**
     * @param reasonRequired           whether the caller must justify the transition
     * @param assignsPublicationNumber whether the transition hands out the publication number
     */
    public record TransitionProperties(
            RequestState source,
            RequestAction action,
            RequestState target,
            boolean reasonRequired,
            boolean assignsPublicationNumber) {

        Transition toTransition() {
            return new Transition(
                    source,
                    action,
                    target,
                    reasonRequired,
                    assignsPublicationNumber);
        }
    }

    public WorkflowDefinition toDefinition() {
        if (transitions == null || transitions.isEmpty()) {
            return WorkflowDefinition.fromStateDiagram();
        }
        if (initialState == null) {
            throw new IllegalStateException(
                    "request-management.workflow.initial-state must be set when transitions are configured");
        }
        Set<Transition> configured = transitions.stream()
                .map(TransitionProperties::toTransition)
                .collect(Collectors.toUnmodifiableSet());
        Set<RequestState> editable = contentEditableStates == null
                ? Set.of()
                : Set.copyOf(contentEditableStates);

        return new WorkflowDefinition(
                initialState,
                configured,
                editable);
    }
}
