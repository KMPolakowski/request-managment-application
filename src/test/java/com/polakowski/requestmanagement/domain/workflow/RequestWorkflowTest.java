package com.polakowski.requestmanagement.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("The request workflow")
class RequestWorkflowTest {

    /**
     * The state diagram of the specification, written out once more by hand so that the test fails
     * if the production definition drifts away from it.
     */
    private static final Map<RequestState, Map<RequestAction, RequestState>> STATE_DIAGRAM = Map.of(
            RequestState.CREATED, Map.of(
                    RequestAction.VERIFY, RequestState.VERIFIED,
                    RequestAction.DELETE, RequestState.DELETED),
            RequestState.VERIFIED, Map.of(
                    RequestAction.ACCEPT, RequestState.ACCEPTED,
                    RequestAction.REJECT, RequestState.REJECTED),
            RequestState.ACCEPTED, Map.of(
                    RequestAction.REJECT, RequestState.REJECTED,
                    RequestAction.PUBLISH, RequestState.PUBLISHED),
            RequestState.PUBLISHED, Map.of(),
            RequestState.REJECTED, Map.of(),
            RequestState.DELETED, Map.of());

    private final RequestWorkflow workflow = new RequestWorkflow(WorkflowDefinition.fromStateDiagram());

    static Stream<Arguments> everyStateAndActionCombination() {
        return Arrays.stream(RequestState.values())
                .flatMap(state -> Arrays.stream(RequestAction.values())
                        .map(action -> Arguments.of(state, action)));
    }

    @DisplayName("allows exactly the transitions drawn in the state diagram")
    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("everyStateAndActionCombination")
    void allowsExactlyTheTransitionsOfTheDiagram(RequestState state, RequestAction action) {
        RequestState expectedTarget = STATE_DIAGRAM.get(state).get(action);

        if (expectedTarget == null) {
            assertThatThrownBy(() -> workflow.transitionFor(state, action))
                    .isInstanceOf(TransitionNotAllowedException.class);
        } else {
            assertThat(workflow.transitionFor(state, action).target()).isEqualTo(expectedTarget);
        }
    }

    @Nested
    @DisplayName("when asked what can be done next")
    class AllowedActions {

        @Test
        @DisplayName("offers verifying and deleting a created request")
        void offersVerifyAndDeleteForACreatedRequest() {
            assertThat(workflow.allowedActions(RequestState.CREATED))
                    .containsExactlyInAnyOrder(RequestAction.VERIFY, RequestAction.DELETE);
        }

        @Test
        @DisplayName("offers rejecting and publishing an accepted request")
        void offersRejectAndPublishForAnAcceptedRequest() {
            assertThat(workflow.allowedActions(RequestState.ACCEPTED))
                    .containsExactlyInAnyOrder(RequestAction.REJECT, RequestAction.PUBLISH);
        }

        @DisplayName("offers nothing once the request reached a terminal state")
        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RequestState.class, names = {"PUBLISHED", "REJECTED", "DELETED"})
        void offersNothingInTerminalStates(RequestState terminalState) {
            assertThat(workflow.allowedActions(terminalState)).isEmpty();
            assertThat(workflow.isTerminal(terminalState)).isTrue();
        }

        @DisplayName("does not consider a state with outgoing transitions terminal")
        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RequestState.class, names = {"CREATED", "VERIFIED", "ACCEPTED"})
        void doesNotConsiderActiveStatesTerminal(RequestState activeState) {
            assertThat(workflow.isTerminal(activeState)).isFalse();
        }
    }

    @Nested
    @DisplayName("when consulted about the rules attached to a transition")
    class TransitionRules {

        @Test
        @DisplayName("requires a reason to delete and to reject")
        void requiresAReasonToDeleteAndToReject() {
            assertThat(reasonRequiredFor(RequestState.CREATED, RequestAction.DELETE)).isTrue();
            assertThat(reasonRequiredFor(RequestState.VERIFIED, RequestAction.REJECT)).isTrue();
            assertThat(reasonRequiredFor(RequestState.ACCEPTED, RequestAction.REJECT)).isTrue();
        }

        @Test
        @DisplayName("does not require a reason to verify, accept or publish")
        void doesNotRequireAReasonForTheHappyPath() {
            assertThat(reasonRequiredFor(RequestState.CREATED, RequestAction.VERIFY)).isFalse();
            assertThat(reasonRequiredFor(RequestState.VERIFIED, RequestAction.ACCEPT)).isFalse();
            assertThat(reasonRequiredFor(RequestState.ACCEPTED, RequestAction.PUBLISH)).isFalse();
        }

        private boolean reasonRequiredFor(RequestState state, RequestAction action) {
            return workflow.transitionFor(state, action).reasonRequired();
        }

        @Test
        @DisplayName("assigns a publication number only when publishing")
        void assignsAPublicationNumberOnlyWhenPublishing() {
            Transition publishing = workflow.transitionFor(RequestState.ACCEPTED, RequestAction.PUBLISH);
            Transition verifying = workflow.transitionFor(RequestState.CREATED, RequestAction.VERIFY);

            assertThat(publishing.assignsPublicationNumber()).isTrue();
            assertThat(verifying.assignsPublicationNumber()).isFalse();
        }

        @Test
        @DisplayName("reports which actions were possible when refusing one")
        void reportsThePossibleActionsWhenRefusing() {
            assertThatThrownBy(() -> workflow.transitionFor(RequestState.CREATED, RequestAction.PUBLISH))
                    .isInstanceOf(TransitionNotAllowedException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(TransitionNotAllowedException.class))
                    .satisfies(exception -> {
                        assertThat(exception.currentState()).isEqualTo(RequestState.CREATED);
                        assertThat(exception.action()).isEqualTo(RequestAction.PUBLISH);
                        assertThat(exception.allowedActions())
                                .containsExactlyInAnyOrder(RequestAction.VERIFY, RequestAction.DELETE);
                    });
        }
    }

    @Nested
    @DisplayName("when asked whether the body may be edited")
    class ContentEditing {

        @DisplayName("allows editing while created or verified")
        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RequestState.class, names = {"CREATED", "VERIFIED"})
        void allowsEditingWhileCreatedOrVerified(RequestState editableState) {
            assertThat(workflow.isContentEditable(editableState)).isTrue();
        }

        @DisplayName("refuses editing in every other state")
        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RequestState.class, names = {"ACCEPTED", "PUBLISHED", "REJECTED", "DELETED"})
        void refusesEditingElsewhere(RequestState frozenState) {
            assertThat(workflow.isContentEditable(frozenState)).isFalse();
        }
    }

    @Nested
    @DisplayName("when configured with a different lifecycle")
    class Configurability {

        @Test
        @DisplayName("follows the configured diagram instead of the default one")
        void followsTheConfiguredDiagram() {
            RequestWorkflow shortcut = new RequestWorkflow(new WorkflowDefinition(
                    RequestState.CREATED,
                    Set.copyOf(List.of(Transition.assigningPublicationNumber(
                            RequestState.CREATED, RequestAction.PUBLISH, RequestState.PUBLISHED))),
                    Set.of(RequestState.CREATED)));

            assertThat(shortcut.transitionFor(RequestState.CREATED, RequestAction.PUBLISH).target())
                    .isEqualTo(RequestState.PUBLISHED);
            assertThatThrownBy(() -> shortcut.transitionFor(RequestState.CREATED, RequestAction.VERIFY))
                    .isInstanceOf(TransitionNotAllowedException.class);
        }

        @Test
        @DisplayName("starts requests in the configured initial state")
        void startsInTheConfiguredInitialState() {
            assertThat(workflow.initialState()).isEqualTo(RequestState.CREATED);
        }

        @Test
        @DisplayName("describes itself for the startup log")
        void describesItself() {
            assertThat(workflow.toString())
                    .contains("initialState=CREATED")
                    .contains("CREATED -VERIFY-> VERIFIED");
        }
    }
}
