package com.polakowski.requestmanagement.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.polakowski.requestmanagement.application.RequestManagement;
import com.polakowski.requestmanagement.application.RequestService;
import com.polakowski.requestmanagement.application.command.ApplyActionCommand;
import com.polakowski.requestmanagement.application.command.ChangeRequestBodyCommand;
import com.polakowski.requestmanagement.application.command.CreateRequestCommand;
import com.polakowski.requestmanagement.domain.exception.ContentNotEditableException;
import com.polakowski.requestmanagement.domain.exception.ReasonRequiredException;
import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import com.polakowski.requestmanagement.testsupport.InMemoryRequestRepository;
import com.polakowski.requestmanagement.testsupport.SequentialPublicationNumberGenerator;
import com.polakowski.requestmanagement.testsupport.SequentialRequestIdGenerator;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Glue turning the scenarios into calls on the application service. */
public class RequestLifecycleSteps {

    private static final Map<RequestState, List<RequestAction>> ROUTE_TO_STATE = Map.of(
            RequestState.CREATED, List.of(),
            RequestState.VERIFIED, List.of(RequestAction.VERIFY),
            RequestState.ACCEPTED, List.of(RequestAction.VERIFY, RequestAction.ACCEPT),
            RequestState.PUBLISHED, List.of(
                    RequestAction.VERIFY,
                    RequestAction.ACCEPT,
                    RequestAction.PUBLISH));

    private final SequentialPublicationNumberGenerator publicationNumbers =
            new SequentialPublicationNumberGenerator(1);
    private final RequestManagement service = new RequestService(
            new InMemoryRequestRepository(),
            new RequestWorkflow(WorkflowDefinition.fromStateDiagram()),
            new SequentialRequestIdGenerator(),
            publicationNumbers,
            Clock.fixed(Instant.parse("2026-04-01T08:00:00Z"), ZoneOffset.UTC));

    private RequestId requestId;
    private RequestId secondRequestId;
    private Throwable failure;

    @Given("a request named {string} with body {string}")
    public void aRequestNamedWithBody(String name, String body) {
        requestId = service.create(new CreateRequestCommand(name, body)).id();
    }

    @Given("another request named {string} with body {string}")
    public void anotherRequestNamedWithBody(String name, String body) {
        secondRequestId = service.create(new CreateRequestCommand(name, body)).id();
    }

    @When("I verify the request")
    public void iVerifyTheRequest() {
        apply(requestId, RequestAction.VERIFY, null);
    }

    @When("I accept the request")
    public void iAcceptTheRequest() {
        apply(requestId, RequestAction.ACCEPT, null);
    }

    @When("I publish the request")
    public void iPublishTheRequest() {
        apply(requestId, RequestAction.PUBLISH, null);
    }

    @When("I reject the request because of {string}")
    public void iRejectTheRequestBecauseOf(String reason) {
        apply(requestId, RequestAction.REJECT, reason);
    }

    @When("I delete the request because of {string}")
    public void iDeleteTheRequestBecauseOf(String reason) {
        apply(requestId, RequestAction.DELETE, reason);
    }

    @When("I take the request to state {word}")
    public void iTakeTheRequestToState(String state) {
        ROUTE_TO_STATE.get(RequestState.valueOf(state))
                .forEach(action -> apply(requestId, action, null));
    }

    @When("I take both requests all the way to publication")
    public void iTakeBothRequestsAllTheWayToPublication() {
        List.of(requestId, secondRequestId).forEach(id -> ROUTE_TO_STATE.get(RequestState.PUBLISHED)
                .forEach(action -> apply(id, action, null)));
    }

    @When("I try to accept the request")
    public void iTryToAcceptTheRequest() {
        failure = catchThrowable(() -> apply(requestId, RequestAction.ACCEPT, null));
    }

    @When("I try to publish the request")
    public void iTryToPublishTheRequest() {
        failure = catchThrowable(() -> apply(requestId, RequestAction.PUBLISH, null));
    }

    @When("I try to {word} the request without a reason")
    public void iTryToActOnTheRequestWithoutAReason(String action) {
        failure = catchThrowable(() ->
                apply(requestId, RequestAction.valueOf(action.toUpperCase()), null));
    }

    @When("I change the body to {string}")
    public void iChangeTheBodyTo(String body) {
        service.changeBody(new ChangeRequestBodyCommand(requestId, body));
    }

    @When("I try to change the body to {string}")
    public void iTryToChangeTheBodyTo(String body) {
        failure = catchThrowable(() -> iChangeTheBodyTo(body));
    }

    @Then("the request is in state {word}")
    public void theRequestIsInState(String state) {
        assertThat(current().state()).isEqualTo(RequestState.valueOf(state));
    }

    @Then("the request has been given a publication number")
    public void theRequestHasBeenGivenAPublicationNumber() {
        assertThat(current().publicationNumber()).isPresent();
    }

    @Then("both requests have different publication numbers")
    public void bothRequestsHaveDifferentPublicationNumbers() {
        assertThat(service.get(requestId).publicationNumber().orElseThrow())
                .isNotEqualTo(service.get(secondRequestId).publicationNumber().orElseThrow());
    }

    @Then("no publication number has been handed out")
    public void noPublicationNumberHasBeenHandedOut() {
        assertThat(publicationNumbers.issued()).isZero();
        assertThat(current().publicationNumber()).isEmpty();
    }

    @Then("the action is refused because the transition is not allowed")
    public void theActionIsRefusedBecauseTheTransitionIsNotAllowed() {
        assertThat(failure).isInstanceOf(TransitionNotAllowedException.class);
    }

    @Then("the action is refused because a reason is required")
    public void theActionIsRefusedBecauseAReasonIsRequired() {
        assertThat(failure).isInstanceOf(ReasonRequiredException.class);
    }

    @Then("the change is refused because the body is not editable")
    public void theChangeIsRefusedBecauseTheBodyIsNotEditable() {
        assertThat(failure).isInstanceOf(ContentNotEditableException.class);
    }

    @Then("the body of the request is {string}")
    public void theBodyOfTheRequestIs(String body) {
        assertThat(current().body()).isEqualTo(body);
    }

    @Then("the audit trail records {string} as the reason of the last change")
    public void theAuditTrailRecordsTheReasonOfTheLastChange(String reason) {
        assertThat(current().history()).last()
                .satisfies(change -> assertThat(change.justification()).contains(reason));
    }

    @Then("^the audit trail reads (.+)$")
    public void theAuditTrailReads(String states) {
        List<String> expected = List.of(states.split(",\\s*"));
        assertThat(current().history()).extracting(change -> change.to().name())
                .containsExactlyElementsOf(expected);
    }

    @And("the request can still be read")
    public void theRequestCanStillBeRead() {
        assertThat(service.get(requestId)).isNotNull();
    }

    private void apply(RequestId id, RequestAction action, String reason) {
        service.apply(new ApplyActionCommand(id, action, reason));
    }

    private Request current() {
        return service.get(requestId);
    }
}
