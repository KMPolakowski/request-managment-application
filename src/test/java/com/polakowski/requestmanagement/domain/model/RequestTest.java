package com.polakowski.requestmanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polakowski.requestmanagement.domain.exception.ContentNotEditableException;
import com.polakowski.requestmanagement.domain.exception.InvalidRequestContentException;
import com.polakowski.requestmanagement.domain.exception.ReasonRequiredException;
import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import com.polakowski.requestmanagement.testsupport.SequentialRequestIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("A request")
class RequestTest {

    private static final Instant CREATION_TIME = Instant.parse("2026-01-15T09:00:00Z");
    private static final LongSupplier NEVER_CALLED = () -> {
        throw new AssertionError("No publication number should have been drawn");
    };

    private final RequestWorkflow workflow = new RequestWorkflow(WorkflowDefinition.fromStateDiagram());
    private final RequestIdGenerator requestIds = new SequentialRequestIdGenerator();
    private final AtomicLong publicationNumbers = new AtomicLong(1000);

    @Nested
    @DisplayName("when created")
    class Creation {

        @Test
        @DisplayName("starts in the initial state of the workflow")
        void startsInTheInitialState() {
            Request request = newRequest();

            assertThat(request.state()).isEqualTo(RequestState.CREATED);
            assertThat(request.name()).isEqualTo("Liquidity buffer review");
            assertThat(request.body()).isEqualTo("Quarterly review of the liquidity buffer");
            assertThat(request.publicationNumber()).isEmpty();
            assertThat(request.createdAt()).isEqualTo(CREATION_TIME);
            assertThat(request.lastModifiedAt()).isEqualTo(CREATION_TIME);
        }

        @Test
        @DisplayName("records its creation as the first audit entry")
        void recordsItsCreation() {
            Request request = newRequest();

            assertThat(request.history()).singleElement().satisfies(entry -> {
                assertThat(entry.sequenceNumber()).isEqualTo(1);
                assertThat(entry.fromState()).isEmpty();
                assertThat(entry.appliedAction()).isEmpty();
                assertThat(entry.to()).isEqualTo(RequestState.CREATED);
                assertThat(entry.occurredAt()).isEqualTo(CREATION_TIME);
            });
        }

        @DisplayName("refuses a missing name")
        @ParameterizedTest(name = "name = \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void refusesAMissingName(String name) {
            assertThatThrownBy(() -> Request.create(requestIds.next(), name, "body", workflow, CREATION_TIME))
                    .isInstanceOf(InvalidRequestContentException.class)
                    .hasMessageContaining("name must not be blank");
        }

        @DisplayName("refuses a missing body")
        @ParameterizedTest(name = "body = \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void refusesAMissingBody(String body) {
            assertThatThrownBy(() -> Request.create(requestIds.next(), "name", body, workflow, CREATION_TIME))
                    .isInstanceOf(InvalidRequestContentException.class)
                    .hasMessageContaining("body must not be blank");
        }

        @Test
        @DisplayName("refuses content longer than the domain allows")
        void refusesOversizedContent() {
            String tooLong = "x".repeat(Request.MAX_NAME_LENGTH + 1);

            assertThatThrownBy(() -> Request.create(
                    requestIds.next(),
                    tooLong,
                    "body",
                    workflow,
                    CREATION_TIME))
                    .isInstanceOf(InvalidRequestContentException.class)
                    .hasMessageContaining("must not exceed");
        }

        @Test
        @DisplayName("trims the surrounding whitespace of what it is given")
        void trimsWhitespace() {
            Request request = Request.create(
                    requestIds.next(), "  a name  ", "  a body  ", workflow, CREATION_TIME);

            assertThat(request.name()).isEqualTo("a name");
            assertThat(request.body()).isEqualTo("a body");
        }
    }

    @Nested
    @DisplayName("when its body is edited")
    class BodyEditing {

        @Test
        @DisplayName("accepts the change while created")
        void acceptsTheChangeWhileCreated() {
            Request request = newRequest();

            request.changeBody("A revised body", workflow, CREATION_TIME.plus(Duration.ofMinutes(5)));

            assertThat(request.body()).isEqualTo("A revised body");
            assertThat(request.lastModifiedAt()).isEqualTo(CREATION_TIME.plus(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("accepts the change while verified")
        void acceptsTheChangeWhileVerified() {
            Request request = newRequest();
            request.apply(RequestAction.VERIFY, null, workflow, CREATION_TIME, NEVER_CALLED);

            request.changeBody("A revised body", workflow, CREATION_TIME);

            assertThat(request.body()).isEqualTo("A revised body");
        }

        @Test
        @DisplayName("refuses the change once accepted")
        void refusesTheChangeOnceAccepted() {
            Request request = acceptedRequest();

            assertThatThrownBy(() -> request.changeBody("Too late", workflow, CREATION_TIME))
                    .isInstanceOf(ContentNotEditableException.class)
                    .hasMessageContaining("while it is ACCEPTED")
                    .hasMessageContaining("[CREATED, VERIFIED]");
            assertThat(request.body()).isEqualTo("Quarterly review of the liquidity buffer");
        }

        @Test
        @DisplayName("refuses a blank body")
        void refusesABlankBody() {
            Request request = newRequest();

            assertThatThrownBy(() -> request.changeBody("  ", workflow, CREATION_TIME))
                    .isInstanceOf(InvalidRequestContentException.class);
        }

        @Test
        @DisplayName("does not add an audit entry, because the state did not change")
        void doesNotAddAnAuditEntry() {
            Request request = newRequest();

            request.changeBody("A revised body", workflow, CREATION_TIME);

            assertThat(request.history()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("when an action is applied")
    class ApplyingActions {

        @Test
        @DisplayName("moves along the state diagram")
        void movesAlongTheStateDiagram() {
            Request request = newRequest();

            request.apply(RequestAction.VERIFY, null, workflow, CREATION_TIME, NEVER_CALLED);

            assertThat(request.state()).isEqualTo(RequestState.VERIFIED);
        }

        @Test
        @DisplayName("refuses an action the current state does not allow")
        void refusesAnImpossibleAction() {
            Request request = newRequest();

            assertThatThrownBy(() ->
                    request.apply(RequestAction.PUBLISH, null, workflow, CREATION_TIME, NEVER_CALLED))
                    .isInstanceOf(TransitionNotAllowedException.class);
            assertThat(request.state()).isEqualTo(RequestState.CREATED);
        }

        @Test
        @DisplayName("refuses to delete without a reason")
        void refusesToDeleteWithoutAReason() {
            Request request = newRequest();

            assertThatThrownBy(() ->
                    request.apply(RequestAction.DELETE, "  ", workflow, CREATION_TIME, NEVER_CALLED))
                    .isInstanceOf(ReasonRequiredException.class)
                    .hasMessageContaining("delete");
            assertThat(request.state()).isEqualTo(RequestState.CREATED);
            assertThat(request.history()).hasSize(1);
        }

        @Test
        @DisplayName("refuses to reject without a reason")
        void refusesToRejectWithoutAReason() {
            Request request = newRequest();
            request.apply(RequestAction.VERIFY, null, workflow, CREATION_TIME, NEVER_CALLED);

            assertThatThrownBy(() ->
                    request.apply(RequestAction.REJECT, null, workflow, CREATION_TIME, NEVER_CALLED))
                    .isInstanceOf(ReasonRequiredException.class);
        }

        @Test
        @DisplayName("refuses a reason longer than the domain allows")
        void refusesAnOversizedReason() {
            Request request = newRequest();
            String tooLong = "x".repeat(Request.MAX_REASON_LENGTH + 1);

            assertThatThrownBy(() ->
                    request.apply(RequestAction.DELETE, tooLong, workflow, CREATION_TIME, NEVER_CALLED))
                    .isInstanceOf(InvalidRequestContentException.class)
                    .hasMessageContaining("reason");
        }

        @Test
        @DisplayName("keeps the reason it was given, trimmed")
        void keepsTheReason() {
            Request request = newRequest();

            request.apply(
                    RequestAction.DELETE,
                    "  duplicate submission  ",
                    workflow,
                    CREATION_TIME,
                    NEVER_CALLED);

            assertThat(request.state()).isEqualTo(RequestState.DELETED);
            assertThat(request.history()).last().satisfies(entry ->
                    assertThat(entry.justification()).contains("duplicate submission"));
        }

        @Test
        @DisplayName("ignores a reason on a transition that does not need one")
        void ignoresAnUnnecessaryReason() {
            Request request = newRequest();

            request.apply(RequestAction.VERIFY, "not needed", workflow, CREATION_TIME, NEVER_CALLED);

            assertThat(request.history()).last().satisfies(entry ->
                    assertThat(entry.justification()).contains("not needed"));
        }

        @Test
        @DisplayName("records every change in order, oldest entry first")
        void recordsEveryChangeInOrder() {
            Request request = acceptedRequest();

            assertThat(request.history()).hasSize(3);
            assertThat(request.history()).extracting(StateChange::sequenceNumber).containsExactly(1, 2, 3);
            assertThat(request.history()).extracting(StateChange::to).containsExactly(
                    RequestState.CREATED, RequestState.VERIFIED, RequestState.ACCEPTED);
            assertThat(request.history()).extracting(StateChange::from).containsExactly(
                    null, RequestState.CREATED, RequestState.VERIFIED);
        }

        @Test
        @DisplayName("hands out a publication number only on publication")
        void handsOutAPublicationNumberOnlyOnPublication() {
            Request request = acceptedRequest();
            assertThat(request.publicationNumber()).isEmpty();

            request.apply(
                    RequestAction.PUBLISH,
                    null,
                    workflow,
                    CREATION_TIME,
                    publicationNumbers::incrementAndGet);

            assertThat(request.state()).isEqualTo(RequestState.PUBLISHED);
            assertThat(request.publicationNumber()).contains(1001L);
        }

        @Test
        @DisplayName("does not burn a publication number when the transition is refused")
        void doesNotBurnAPublicationNumberWhenRefused() {
            Request request = newRequest();

            assertThatThrownBy(() -> request.apply(
                    RequestAction.PUBLISH,
                    null,
                    workflow,
                    CREATION_TIME,
                    publicationNumbers::incrementAndGet))
                    .isInstanceOf(TransitionNotAllowedException.class);

            assertThat(publicationNumbers).hasValue(1000);
        }

        @Test
        @DisplayName("cannot be touched once it reached a terminal state")
        void cannotBeTouchedOnceTerminal() {
            Request request = acceptedRequest();
            request.apply(
                    RequestAction.PUBLISH,
                    null,
                    workflow,
                    CREATION_TIME,
                    publicationNumbers::incrementAndGet);

            assertThatThrownBy(() ->
                    request.apply(RequestAction.REJECT, "too late", workflow, CREATION_TIME, NEVER_CALLED))
                    .isInstanceOf(TransitionNotAllowedException.class);
        }
    }

    @Nested
    @DisplayName("when stored and restored")
    class Snapshots {

        @Test
        @DisplayName("keeps every field and its whole history")
        void keepsEveryField() {
            Request original = acceptedRequest();
            original.apply(RequestAction.PUBLISH, null, workflow, CREATION_TIME, () -> 42L);

            Request restored = Request.fromSnapshot(original.toSnapshot());

            assertThat(restored.id()).isEqualTo(original.id());
            assertThat(restored.name()).isEqualTo(original.name());
            assertThat(restored.body()).isEqualTo(original.body());
            assertThat(restored.state()).isEqualTo(RequestState.PUBLISHED);
            assertThat(restored.publicationNumber()).contains(42L);
            assertThat(restored.createdAt()).isEqualTo(original.createdAt());
            assertThat(restored.lastModifiedAt()).isEqualTo(original.lastModifiedAt());
            assertThat(restored.history()).isEqualTo(original.history());
        }

        @Test
        @DisplayName("is identified by its identifier alone")
        void isIdentifiedByItsIdentifier() {
            Request request = newRequest();
            Request sameIdentity = Request.fromSnapshot(request.toSnapshot());
            sameIdentity.changeBody("A different body", workflow, CREATION_TIME);

            assertThat(request).isEqualTo(sameIdentity).hasSameHashCodeAs(sameIdentity);
            assertThat(request).isNotEqualTo(newRequest());
            assertThat(request.toString()).contains(request.id().toString()).contains("CREATED");
        }

        @Test
        @DisplayName("hands out a history that cannot be tampered with")
        void handsOutAnUnmodifiableHistory() {
            Request request = newRequest();

            assertThatThrownBy(() -> request.history().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private Request newRequest() {
        return Request.create(
                requestIds.next(),
                "Liquidity buffer review",
                "Quarterly review of the liquidity buffer",
                workflow,
                CREATION_TIME);
    }

    private Request acceptedRequest() {
        Request request = newRequest();
        request.apply(RequestAction.VERIFY, null, workflow, CREATION_TIME, NEVER_CALLED);
        request.apply(RequestAction.ACCEPT, null, workflow, CREATION_TIME, NEVER_CALLED);
        return request;
    }
}
