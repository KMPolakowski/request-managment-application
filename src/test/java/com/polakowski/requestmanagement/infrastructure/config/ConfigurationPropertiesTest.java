package com.polakowski.requestmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The external configuration")
class ConfigurationPropertiesTest {

    @Nested
    @DisplayName("of the workflow")
    class Workflow {

        @Test
        @DisplayName("falls back to the state diagram when nothing is configured")
        void fallsBackToTheStateDiagram() {
            WorkflowDefinition definition = new WorkflowProperties(null, null, null).toDefinition();

            assertThat(definition).isEqualTo(WorkflowDefinition.fromStateDiagram());
        }

        @Test
        @DisplayName("falls back to the state diagram when the transition list is empty")
        void fallsBackWhenTheTransitionListIsEmpty() {
            WorkflowDefinition definition =
                    new WorkflowProperties(RequestState.CREATED, List.of(), List.of()).toDefinition();

            assertThat(definition).isEqualTo(WorkflowDefinition.fromStateDiagram());
        }

        @Test
        @DisplayName("builds the configured lifecycle")
        void buildsTheConfiguredLifecycle() {
            WorkflowProperties properties = new WorkflowProperties(
                    RequestState.CREATED,
                    List.of(RequestState.CREATED),
                    List.of(transition(RequestAction.PUBLISH, RequestState.PUBLISHED, false, true)));

            WorkflowDefinition definition = properties.toDefinition();

            assertThat(definition.transitions()).singleElement().satisfies(transition -> {
                assertThat(transition.target()).isEqualTo(RequestState.PUBLISHED);
                assertThat(transition.assignsPublicationNumber()).isTrue();
            });
            assertThat(definition.contentEditableStates()).containsExactly(RequestState.CREATED);
        }

        @Test
        @DisplayName("treats a lifecycle without editable states as one where nothing can be edited")
        void treatsMissingEditableStatesAsNone() {
            WorkflowProperties properties = new WorkflowProperties(
                    RequestState.CREATED,
                    null,
                    List.of(transition(RequestAction.VERIFY, RequestState.VERIFIED, false, false)));

            assertThat(properties.toDefinition().contentEditableStates()).isEmpty();
        }

        @Test
        @DisplayName("refuses a configured lifecycle without an initial state")
        void refusesAConfiguredLifecycleWithoutAnInitialState() {
            WorkflowProperties properties = new WorkflowProperties(
                    null,
                    List.of(),
                    List.of(transition(RequestAction.VERIFY, RequestState.VERIFIED, false, false)));

            assertThatThrownBy(properties::toDefinition)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("initial-state");
        }
    }

    private static WorkflowProperties.TransitionProperties transition(
            RequestAction action,
            RequestState target,
            boolean reasonRequired,
            boolean assignsPublicationNumber) {
        return new WorkflowProperties.TransitionProperties(
                RequestState.CREATED,
                action,
                target,
                reasonRequired,
                assignsPublicationNumber);
    }

    @Nested
    @DisplayName("of the browsing endpoint")
    class Api {

        private final ApiProperties properties = new ApiProperties(10, 100);

        @Test
        @DisplayName("applies the default size when the caller asks for none")
        void appliesTheDefaultSize() {
            assertThat(properties.resolvePageSize(null)).isEqualTo(10);
            assertThat(properties.resolvePageSize(0)).isEqualTo(10);
            assertThat(properties.resolvePageSize(-3)).isEqualTo(10);
        }

        @Test
        @DisplayName("honours a reasonable size and caps an unreasonable one")
        void honoursAReasonableSize() {
            assertThat(properties.resolvePageSize(25)).isEqualTo(25);
            assertThat(properties.resolvePageSize(5_000)).isEqualTo(100);
        }

        @Test
        @DisplayName("refuses a nonsensical configuration")
        void refusesANonsensicalConfiguration() {
            assertThatThrownBy(() -> new ApiProperties(0, 100))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("default-page-size");
            assertThatThrownBy(() -> new ApiProperties(50, 10))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("max-page-size");
        }
    }

    @Nested
    @DisplayName("of the publication number")
    class Publication {

        @Test
        @DisplayName("keeps the configured statement")
        void keepsTheConfiguredStatement() {
            assertThat(new PublicationProperties("SELECT nextval('seq')").sequenceQuery())
                    .isEqualTo("SELECT nextval('seq')");
        }

        @Test
        @DisplayName("refuses to start without a statement")
        void refusesToStartWithoutAStatement() {
            assertThatThrownBy(() -> new PublicationProperties(" "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sequence-query");
        }
    }
}
