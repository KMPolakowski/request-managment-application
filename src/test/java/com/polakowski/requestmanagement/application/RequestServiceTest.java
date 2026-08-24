package com.polakowski.requestmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polakowski.requestmanagement.application.command.ApplyActionCommand;
import com.polakowski.requestmanagement.application.command.ChangeRequestBodyCommand;
import com.polakowski.requestmanagement.application.command.CreateRequestCommand;
import com.polakowski.requestmanagement.domain.exception.ContentNotEditableException;
import com.polakowski.requestmanagement.domain.exception.ReasonRequiredException;
import com.polakowski.requestmanagement.domain.exception.RequestNotFoundException;
import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import com.polakowski.requestmanagement.testsupport.InMemoryRequestRepository;
import com.polakowski.requestmanagement.testsupport.SequentialPublicationNumberGenerator;
import com.polakowski.requestmanagement.testsupport.SequentialRequestIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The request service")
class RequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");

    private final InMemoryRequestRepository repository = new InMemoryRequestRepository();
    private final SequentialPublicationNumberGenerator publicationNumbers =
            new SequentialPublicationNumberGenerator(500);
    private final RequestWorkflow workflow = new RequestWorkflow(WorkflowDefinition.fromStateDiagram());
    private final RequestIdGenerator requestIds = new SequentialRequestIdGenerator();
    private final RequestManagement service = new RequestService(
            repository,
            workflow,
            requestIds,
            publicationNumbers,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("when creating a request")
    class Creating {

        @Test
        @DisplayName("stores it in the initial state and stamps it with the current time")
        void storesItInTheInitialState() {
            Request created = service.create(new CreateRequestCommand("Basel III report", "Draft content"));

            assertThat(created.state()).isEqualTo(RequestState.CREATED);
            assertThat(created.createdAt()).isEqualTo(NOW);
            assertThat(repository.findById(created.id())).isPresent();
        }

        @Test
        @DisplayName("gives every request its own identity")
        void givesEveryRequestItsOwnIdentity() {
            Request first = service.create(new CreateRequestCommand("First", "Content"));
            Request second = service.create(new CreateRequestCommand("Second", "Content"));

            assertThat(first.id()).isNotEqualTo(second.id());
            assertThat(repository.size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("when editing a request")
    class Editing {

        @Test
        @DisplayName("stores the new body")
        void storesTheNewBody() {
            Request created = service.create(new CreateRequestCommand("Basel III report", "Draft content"));

            Request updated = service.changeBody(new ChangeRequestBodyCommand(created.id(), "Final content"));

            assertThat(updated.body()).isEqualTo("Final content");
            assertThat(service.get(created.id()).body()).isEqualTo("Final content");
        }

        @Test
        @DisplayName("refuses to edit once the request left the editable states")
        void refusesToEditOutsideTheEditableStates() {
            RequestId id = acceptedRequest();

            assertThatThrownBy(() -> service.changeBody(new ChangeRequestBodyCommand(id, "Too late")))
                    .isInstanceOf(ContentNotEditableException.class);
        }

        @Test
        @DisplayName("fails when the request does not exist")
        void failsForAnUnknownRequest() {
            RequestId unknown = requestIds.next();

            assertThatThrownBy(() -> service.changeBody(new ChangeRequestBodyCommand(unknown, "Content")))
                    .isInstanceOf(RequestNotFoundException.class)
                    .hasMessageContaining(unknown.toString());
        }
    }

    @Nested
    @DisplayName("when moving a request along its lifecycle")
    class Transitioning {

        @Test
        @DisplayName("walks the happy path up to publication")
        void walksTheHappyPath() {
            Request created = service.create(new CreateRequestCommand("Basel III report", "Content"));

            service.apply(ApplyActionCommand.withoutReason(created.id(), RequestAction.VERIFY));
            service.apply(ApplyActionCommand.withoutReason(created.id(), RequestAction.ACCEPT));
            Request published = service.apply(
                    ApplyActionCommand.withoutReason(created.id(), RequestAction.PUBLISH));

            assertThat(published.state()).isEqualTo(RequestState.PUBLISHED);
            assertThat(published.publicationNumber()).contains(500L);
        }

        @Test
        @DisplayName("gives every published request a different number")
        void givesEveryPublishedRequestADifferentNumber() {
            long first = publish(acceptedRequest());
            long second = publish(acceptedRequest());

            assertThat(first).isNotEqualTo(second);
        }

        private long publish(RequestId requestId) {
            return service
                    .apply(ApplyActionCommand.withoutReason(requestId, RequestAction.PUBLISH))
                    .publicationNumber()
                    .orElseThrow();
        }

        @Test
        @DisplayName("refuses a transition the state diagram does not allow")
        void refusesAnImpossibleTransition() {
            Request created = service.create(new CreateRequestCommand("Basel III report", "Content"));

            assertThatThrownBy(() -> service.apply(
                    ApplyActionCommand.withoutReason(created.id(), RequestAction.PUBLISH)))
                    .isInstanceOf(TransitionNotAllowedException.class);
            assertThat(service.get(created.id()).state()).isEqualTo(RequestState.CREATED);
        }

        @Test
        @DisplayName("refuses to reject without a reason and leaves the request untouched")
        void refusesToRejectWithoutAReason() {
            RequestId id = acceptedRequest();

            assertThatThrownBy(() ->
                    service.apply(new ApplyActionCommand(id, RequestAction.REJECT, null)))
                    .isInstanceOf(ReasonRequiredException.class);
            assertThat(service.get(id).state()).isEqualTo(RequestState.ACCEPTED);
            assertThat(publicationNumbers.issued()).isEqualTo(499);
        }

        @Test
        @DisplayName("keeps the whole history of what happened")
        void keepsTheWholeHistory() {
            RequestId id = acceptedRequest();
            service.apply(new ApplyActionCommand(id, RequestAction.REJECT, "Incomplete supporting evidence"));

            assertThat(service.get(id).history())
                    .extracting(change -> change.to().name())
                    .containsExactly("CREATED", "VERIFIED", "ACCEPTED", "REJECTED");
            assertThat(service.get(id).history()).last()
                    .satisfies(change ->
                            assertThat(change.justification()).contains("Incomplete supporting evidence"));
        }

        @Test
        @DisplayName("fails when the request does not exist")
        void failsForAnUnknownRequest() {
            assertThatThrownBy(() -> service.apply(
                    ApplyActionCommand.withoutReason(requestIds.next(), RequestAction.VERIFY)))
                    .isInstanceOf(RequestNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("when browsing requests")
    class Browsing {

        @Test
        @DisplayName("returns the most recent requests first")
        void returnsTheMostRecentFirst() {
            RequestManagement movingClockService = serviceWithMovingClock();
            movingClockService.create(new CreateRequestCommand("Oldest", "Content"));
            movingClockService.create(new CreateRequestCommand("Newest", "Content"));

            PageResult<Request> page = movingClockService.browse(
                    RequestSearchCriteria.none(), PageQuery.of(0, 10));

            assertThat(page.content()).extracting(Request::name).containsExactly("Newest", "Oldest");
        }

        @Test
        @DisplayName("filters by name fragment, ignoring case")
        void filtersByNameFragment() {
            service.create(new CreateRequestCommand("Liquidity coverage ratio", "Content"));
            service.create(new CreateRequestCommand("Net stable funding", "Content"));

            PageResult<Request> page = service.browse(
                    RequestSearchCriteria.of("liquidity", null), PageQuery.of(0, 10));

            assertThat(page.content()).extracting(Request::name).containsExactly("Liquidity coverage ratio");
            assertThat(page.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("filters by state")
        void filtersByState() {
            service.create(new CreateRequestCommand("Still a draft", "Content"));
            RequestId verified = service.create(new CreateRequestCommand("Checked", "Content")).id();
            service.apply(ApplyActionCommand.withoutReason(verified, RequestAction.VERIFY));

            PageResult<Request> page = service.browse(
                    RequestSearchCriteria.of(null, RequestState.VERIFIED), PageQuery.of(0, 10));

            assertThat(page.content()).extracting(Request::name).containsExactly("Checked");
        }

        @Test
        @DisplayName("cuts the result into pages")
        void cutsTheResultIntoPages() {
            for (int index = 0; index < 5; index++) {
                service.create(new CreateRequestCommand("Request " + index, "Content"));
            }

            PageResult<Request> firstPage = service.browse(RequestSearchCriteria.none(), PageQuery.of(0, 2));
            PageResult<Request> lastPage = service.browse(RequestSearchCriteria.none(), PageQuery.of(2, 2));

            assertThat(firstPage.content()).hasSize(2);
            assertThat(firstPage.totalElements()).isEqualTo(5);
            assertThat(firstPage.totalPages()).isEqualTo(3);
            assertThat(firstPage.hasNext()).isTrue();
            assertThat(lastPage.content()).hasSize(1);
            assertThat(lastPage.hasNext()).isFalse();
        }

        private RequestManagement serviceWithMovingClock() {
            return new RequestService(
                    new InMemoryRequestRepository(),
                    workflow,
                    requestIds,
                    publicationNumbers,
                    new MovingClock(NOW, Duration.ofSeconds(1)));
        }
    }

    private RequestId acceptedRequest() {
        Request created = service.create(new CreateRequestCommand("Basel III report", "Content"));
        service.apply(ApplyActionCommand.withoutReason(created.id(), RequestAction.VERIFY));
        service.apply(ApplyActionCommand.withoutReason(created.id(), RequestAction.ACCEPT));
        return created.id();
    }

    /** A clock that advances by a fixed step on every read, so that creation order is observable. */
    private static final class MovingClock extends Clock {

        private final Duration step;
        private Instant current;

        private MovingClock(Instant start, Duration step) {
            this.current = start;
            this.step = step;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            current = current.plus(step);
            return current;
        }
    }
}
