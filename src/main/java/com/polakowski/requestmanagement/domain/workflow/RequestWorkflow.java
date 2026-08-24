package com.polakowski.requestmanagement.domain.workflow;

import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Domain service answering every question about the request lifecycle.
 *
 * <p>It is the single place that knows the state diagram, and it knows it only through the
 * {@link WorkflowDefinition} it was built from. Nothing about a particular lifecycle is compiled
 * into this class, so a new state or a new action is a configuration change.
 */
public final class RequestWorkflow {

    private final WorkflowDefinition definition;
    private final Map<RequestState, Map<RequestAction, Transition>> transitionsByState;

    public RequestWorkflow(WorkflowDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.transitionsByState = index(definition.transitions());
    }

    /** The state in which a newly created request starts. */
    public RequestState initialState() {
        return definition.initialState();
    }

    /**
     * Resolves the transition to apply, or fails when the action is not permitted in that state.
     *
     * @throws TransitionNotAllowedException when the state diagram has no such edge
     */
    public Transition transitionFor(RequestState source, RequestAction action) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(action, "action must not be null");

        Map<RequestAction, Transition> leavingSource = transitionsByState.getOrDefault(source, Map.of());
        Transition transition = leavingSource.get(action);

        if (transition == null) {
            throw new TransitionNotAllowedException(source, action, allowedActions(source));
        }

        return transition;
    }

    /** Actions that may currently be applied to a request in the given state. */
    public Set<RequestAction> allowedActions(RequestState state) {
        Map<RequestAction, Transition> leavingState = transitionsByState.get(state);

        return leavingState == null ? Set.of() : Set.copyOf(leavingState.keySet());
    }

    /** Whether the body of a request in the given state may still be modified. */
    public boolean isContentEditable(RequestState state) {
        return definition.contentEditableStates().contains(state);
    }

    /** States in which the body of a request may be modified. */
    public Set<RequestState> contentEditableStates() {
        return definition.contentEditableStates();
    }

    /** A state is terminal when no action can move a request out of it. */
    public boolean isTerminal(RequestState state) {
        return allowedActions(state).isEmpty();
    }

    private static Map<RequestState, Map<RequestAction, Transition>> index(Set<Transition> transitions) {
        Map<RequestState, Map<RequestAction, Transition>> index = new HashMap<>();

        for (Transition transition : transitions) {
            index
                    .computeIfAbsent(transition.source(), state -> new HashMap<>())
                    .put(transition.action(), transition);
        }
        index.replaceAll((state, byAction) -> Map.copyOf(byAction));

        return Map.copyOf(index);
    }

    @Override
    public String toString() {
        List<String> edges = definition.transitions().stream()
                .map(Transition::toString)
                .sorted()
                .toList();
        List<String> editableStates = definition.contentEditableStates().stream()
                .map(Enum::name)
                .sorted()
                .toList();

        return "RequestWorkflow[initialState=%s, transitions=%s, contentEditableStates=%s]".formatted(
                definition.initialState(),
                edges,
                editableStates);
    }
}
