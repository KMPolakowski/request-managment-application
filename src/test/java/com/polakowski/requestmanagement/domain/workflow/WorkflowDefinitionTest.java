package com.polakowski.requestmanagement.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A workflow definition")
class WorkflowDefinitionTest {

    @Test
    @DisplayName("describes the six transitions of the state diagram")
    void describesTheStateDiagram() {
        WorkflowDefinition definition = WorkflowDefinition.fromStateDiagram();

        assertThat(definition.transitions()).hasSize(6);
        assertThat(definition.initialState()).isEqualTo(RequestState.CREATED);
        assertThat(definition.contentEditableStates())
                .containsExactlyInAnyOrder(RequestState.CREATED, RequestState.VERIFIED);
    }

    @Test
    @DisplayName("rejects a lifecycle without any transition")
    void rejectsAnEmptyLifecycle() {
        assertThatThrownBy(() -> new WorkflowDefinition(RequestState.CREATED, Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one transition");
    }

    @Test
    @DisplayName("rejects an ambiguous lifecycle where one action leads to two states")
    void rejectsAnAmbiguousLifecycle() {
        Set<Transition> ambiguous = Set.copyOf(List.of(
                Transition.of(RequestState.CREATED, RequestAction.VERIFY, RequestState.VERIFIED),
                Transition.of(RequestState.CREATED, RequestAction.VERIFY, RequestState.ACCEPTED)));

        assertThatThrownBy(() -> new WorkflowDefinition(RequestState.CREATED, ambiguous, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ambiguous workflow");
    }

    @Test
    @DisplayName("rejects a transition that leads back to its own state")
    void rejectsASelfTransition() {
        assertThatThrownBy(() ->
                Transition.of(RequestState.CREATED, RequestAction.VERIFY, RequestState.CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self transitions");
    }

    @Test
    @DisplayName("rejects an incomplete transition")
    void rejectsAnIncompleteTransition() {
        assertThatThrownBy(() -> Transition.of(null, RequestAction.VERIFY, RequestState.VERIFIED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkflowDefinition(null, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("reads as the edge it describes")
    void readsAsTheEdgeItDescribes() {
        assertThat(Transition.of(RequestState.CREATED, RequestAction.VERIFY, RequestState.VERIFIED))
                .hasToString("CREATED -VERIFY-> VERIFIED");
    }
}
