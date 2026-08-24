package com.polakowski.requestmanagement.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polakowski.requestmanagement.application.RequestManagement;
import com.polakowski.requestmanagement.application.command.ApplyActionCommand;
import com.polakowski.requestmanagement.application.command.ChangeRequestBodyCommand;
import com.polakowski.requestmanagement.application.command.CreateRequestCommand;
import com.polakowski.requestmanagement.domain.exception.ContentNotEditableException;
import com.polakowski.requestmanagement.domain.exception.InvalidRequestContentException;
import com.polakowski.requestmanagement.domain.exception.ReasonRequiredException;
import com.polakowski.requestmanagement.domain.exception.RequestNotFoundException;
import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.model.RequestSnapshot;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import com.polakowski.requestmanagement.infrastructure.config.ApiProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RequestController.class)
@Import({DefaultRequestResponseAssembler.class, RequestControllerTest.WebTestConfiguration.class})
@DisplayName("The requests endpoint")
class RequestControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-04T12:00:00Z");

    @TestConfiguration
    static class WebTestConfiguration {

        @Bean
        RequestWorkflow requestWorkflow() {
            return new RequestWorkflow(WorkflowDefinition.fromStateDiagram());
        }

        @Bean
        ApiProperties apiProperties() {
            return new ApiProperties(10, 100);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestManagement requests;

    @BeforeEach
    void isolateEachScenario() {
        // The web slice keeps one context for the whole class; starting from a clean mock keeps the
        // interaction assertions below about the test at hand and nothing else.
        Mockito.reset(requests);
    }

    @Nested
    @DisplayName("when creating a request")
    class Creating {

        @Test
        @DisplayName("answers 201 with the created request and its location")
        void answersCreated() throws Exception {
            given(requests.create(any())).willReturn(sampleRequest(RequestState.CREATED, null));

            mockMvc.perform(post("/api/v1/requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Basel III report", "body": "Draft content"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", Matchers.endsWith(
                            "/api/v1/requests/" + SAMPLE_ID)))
                    .andExpect(jsonPath("$.state").value("CREATED"))
                    .andExpect(jsonPath("$.name").value("Basel III report"))
                    .andExpect(jsonPath("$.bodyEditable").value(true))
                    .andExpect(jsonPath("$.allowedActions").value(
                            Matchers.containsInAnyOrder("DELETE", "VERIFY")));

            ArgumentCaptor<CreateRequestCommand> command =
                    ArgumentCaptor.forClass(CreateRequestCommand.class);
            then(requests).should().create(command.capture());

            assertThat(command.getValue().name()).isEqualTo("Basel III report");
        }

        @Test
        @DisplayName("answers 400 when the name is missing")
        void answersBadRequestWithoutAName() throws Exception {
            mockMvc.perform(post("/api/v1/requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "Draft content"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.violations.name").value("name is mandatory"));

            then(requests).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("answers 400 when the body is missing")
        void answersBadRequestWithoutABody() throws Exception {
            mockMvc.perform(post("/api/v1/requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Basel III report"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations.body").value("body is mandatory"));
        }
    }

    @Nested
    @DisplayName("when reading requests")
    class Reading {

        @Test
        @DisplayName("answers 200 with the request")
        void answersWithTheRequest() throws Exception {
            given(requests.get(RequestId.of(SAMPLE_ID)))
                    .willReturn(sampleRequest(RequestState.PUBLISHED, 77L));

            mockMvc.perform(get("/api/v1/requests/{id}", SAMPLE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicationNumber").value(77))
                    .andExpect(jsonPath("$.bodyEditable").value(false))
                    .andExpect(jsonPath("$.allowedActions").isEmpty());
        }

        @Test
        @DisplayName("answers 404 when the request is unknown")
        void answersNotFound() throws Exception {
            given(requests.get(any())).willThrow(new RequestNotFoundException(RequestId.of(SAMPLE_ID)));

            mockMvc.perform(get("/api/v1/requests/{id}", SAMPLE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Request not found"))
                    .andExpect(jsonPath("$.requestId").value(SAMPLE_ID.toString()));
        }

        @Test
        @DisplayName("answers 400 when the identifier is not a UUID")
        void answersBadRequestForAMalformedIdentifier() throws Exception {
            mockMvc.perform(get("/api/v1/requests/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.parameter").value("id"));
        }

        @Test
        @DisplayName("answers 200 with the audit trail")
        void answersWithTheAuditTrail() throws Exception {
            given(requests.get(any())).willReturn(verifiedRequest());

            mockMvc.perform(get("/api/v1/requests/{id}/history", SAMPLE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].to").value("CREATED"))
                    .andExpect(jsonPath("$[1].from").value("CREATED"))
                    .andExpect(jsonPath("$[1].to").value("VERIFIED"))
                    .andExpect(jsonPath("$[1].action").value("VERIFY"));
        }
    }

    @Nested
    @DisplayName("when browsing requests")
    class Browsing {

        @Test
        @DisplayName("applies the default page size of ten")
        void appliesTheDefaultPageSize() throws Exception {
            List<Request> found = List.of(sampleRequest(RequestState.CREATED, null));
            given(requests.browse(any(), any()))
                    .willReturn(new PageResult<>(found, 0, 10, 1));

            mockMvc.perform(get("/api/v1/requests"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(SAMPLE_ID.toString()));

            ArgumentCaptor<PageQuery> page = ArgumentCaptor.forClass(PageQuery.class);
            then(requests).should().browse(any(), page.capture());

            assertThat(page.getValue().size()).isEqualTo(10);
        }

        @Test
        @DisplayName("caps an unreasonably large page size")
        void capsThePageSize() throws Exception {
            given(requests.browse(any(), any())).willReturn(new PageResult<>(List.of(), 0, 100, 0));

            mockMvc.perform(get("/api/v1/requests").param("size", "5000"))
                    .andExpect(status().isOk());

            ArgumentCaptor<PageQuery> page = ArgumentCaptor.forClass(PageQuery.class);
            then(requests).should().browse(any(), page.capture());

            assertThat(page.getValue().size()).isEqualTo(100);
        }

        @Test
        @DisplayName("passes the name and state filters on to the application")
        void passesTheFiltersOn() throws Exception {
            given(requests.browse(any(), any())).willReturn(new PageResult<>(List.of(), 0, 10, 0));

            mockMvc.perform(get("/api/v1/requests").param("name", "basel").param("state", "VERIFIED"))
                    .andExpect(status().isOk());

            ArgumentCaptor<RequestSearchCriteria> criteria =
                    ArgumentCaptor.forClass(RequestSearchCriteria.class);
            then(requests).should().browse(criteria.capture(), any());

            assertThat(criteria.getValue())
                    .isEqualTo(RequestSearchCriteria.of("basel", RequestState.VERIFIED));
        }

        @Test
        @DisplayName("answers 400 for an unknown state")
        void answersBadRequestForAnUnknownState() throws Exception {
            mockMvc.perform(get("/api/v1/requests").param("state", "ARCHIVED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.parameter").value("state"));
        }

        @Test
        @DisplayName("answers 400 for a negative page index")
        void answersBadRequestForANegativePage() throws Exception {
            mockMvc.perform(get("/api/v1/requests").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid parameters"));
        }
    }

    @Nested
    @DisplayName("when moving a request along its lifecycle")
    class Transitioning {

        @Test
        @DisplayName("verifies, accepts and publishes without asking for a reason")
        void appliesTheHappyPathActions() throws Exception {
            given(requests.apply(any())).willReturn(sampleRequest(RequestState.VERIFIED, null));

            mockMvc.perform(post("/api/v1/requests/{id}/verify", SAMPLE_ID)).andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/requests/{id}/accept", SAMPLE_ID)).andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/requests/{id}/publish", SAMPLE_ID)).andExpect(status().isOk());

            ArgumentCaptor<ApplyActionCommand> commands =
                    ArgumentCaptor.forClass(ApplyActionCommand.class);
            then(requests).should(Mockito.times(3)).apply(commands.capture());

            assertThat(commands.getAllValues())
                    .extracting(ApplyActionCommand::action)
                    .containsExactly(RequestAction.VERIFY, RequestAction.ACCEPT, RequestAction.PUBLISH);
        }

        @Test
        @DisplayName("passes the reason on when rejecting")
        void passesTheReasonOnWhenRejecting() throws Exception {
            given(requests.apply(any())).willReturn(sampleRequest(RequestState.REJECTED, null));

            mockMvc.perform(post("/api/v1/requests/{id}/reject", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "Evidence missing"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("REJECTED"));

            ArgumentCaptor<ApplyActionCommand> command =
                    ArgumentCaptor.forClass(ApplyActionCommand.class);
            then(requests).should().apply(command.capture());

            assertThat(command.getValue().reason()).isEqualTo("Evidence missing");
        }

        @Test
        @DisplayName("answers 400 when deleting without a reason")
        void answersBadRequestWhenDeletingWithoutAReason() throws Exception {
            mockMvc.perform(post("/api/v1/requests/{id}/delete", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "  "}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations.reason").value("reason is mandatory"));

            then(requests).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("answers 400 when the domain itself asks for a reason")
        void answersBadRequestWhenTheDomainAsksForAReason() throws Exception {
            given(requests.apply(any())).willThrow(new ReasonRequiredException(RequestAction.REJECT));

            mockMvc.perform(post("/api/v1/requests/{id}/reject", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reason": "Evidence missing"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Reason required"));
        }

        @Test
        @DisplayName("answers 409 when the transition is not allowed and says what is")
        void answersConflictForAnImpossibleTransition() throws Exception {
            willThrow(new TransitionNotAllowedException(
                    RequestState.CREATED, RequestAction.PUBLISH,
                    Set.of(RequestAction.VERIFY, RequestAction.DELETE)))
                    .given(requests).apply(any());

            mockMvc.perform(post("/api/v1/requests/{id}/publish", SAMPLE_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Transition not allowed"))
                    .andExpect(jsonPath("$.currentState").value("CREATED"))
                    .andExpect(jsonPath("$.attemptedAction").value("PUBLISH"))
                    .andExpect(jsonPath("$.allowedActions").value(
                            Matchers.contains("DELETE", "VERIFY")));
        }
    }

    @Nested
    @DisplayName("when editing the body")
    class Editing {

        @Test
        @DisplayName("answers 200 with the updated request")
        void answersWithTheUpdatedRequest() throws Exception {
            given(requests.changeBody(any())).willReturn(sampleRequest(RequestState.CREATED, null));

            mockMvc.perform(put("/api/v1/requests/{id}/body", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "Revised content"}"""))
                    .andExpect(status().isOk());

            ArgumentCaptor<ChangeRequestBodyCommand> command =
                    ArgumentCaptor.forClass(ChangeRequestBodyCommand.class);
            then(requests).should().changeBody(command.capture());

            assertThat(command.getValue().body()).isEqualTo("Revised content");
        }

        @Test
        @DisplayName("answers 409 when the state does not allow editing")
        void answersConflictWhenNotEditable() throws Exception {
            given(requests.changeBody(any())).willThrow(new ContentNotEditableException(
                    RequestState.ACCEPTED, Set.of(RequestState.CREATED, RequestState.VERIFIED)));

            mockMvc.perform(put("/api/v1/requests/{id}/body", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "Revised content"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Body is not editable"))
                    .andExpect(jsonPath("$.editableStates").value(
                            Matchers.contains("CREATED", "VERIFIED")));
        }

        @Test
        @DisplayName("answers 409 when the request was modified concurrently")
        void answersConflictOnConcurrentModification() throws Exception {
            given(requests.changeBody(any()))
                    .willThrow(new ObjectOptimisticLockingFailureException("request", SAMPLE_ID));

            mockMvc.perform(put("/api/v1/requests/{id}/body", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "Revised content"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Concurrent modification"));
        }

        @Test
        @DisplayName("answers 400 when the domain refuses the content")
        void answersBadRequestWhenTheDomainRefusesTheContent() throws Exception {
            given(requests.changeBody(any()))
                    .willThrow(new InvalidRequestContentException(
                            "body",
                            "body must not exceed 10000 characters"));

            mockMvc.perform(put("/api/v1/requests/{id}/body", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "Revised content"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request content"))
                    .andExpect(jsonPath("$.field").value("body"));
        }

        @Test
        @DisplayName("answers 400 when the new body is empty")
        void answersBadRequestForAnEmptyBody() throws Exception {
            mockMvc.perform(put("/api/v1/requests/{id}/body", SAMPLE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": ""}"""))
                    .andExpect(status().isBadRequest());
        }
    }

    private static final UUID SAMPLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Request sampleRequest(RequestState state, Long publicationNumber) {
        RequestWorkflow workflow = new RequestWorkflow(WorkflowDefinition.fromStateDiagram());
        Request request = Request.create(
                RequestId.of(SAMPLE_ID),
                "Basel III report",
                "Draft content",
                workflow,
                NOW);

        RequestSnapshot snapshot = new RequestSnapshot(
                request.id(),
                request.name(),
                request.body(),
                state,
                publicationNumber,
                NOW,
                NOW,
                request.history());

        return Request.fromSnapshot(snapshot);
    }

    private static Request verifiedRequest() {
        RequestWorkflow workflow = new RequestWorkflow(WorkflowDefinition.fromStateDiagram());
        Request request = Request.create(
                RequestId.of(SAMPLE_ID),
                "Basel III report",
                "Draft content",
                workflow,
                NOW);

        request.apply(RequestAction.VERIFY, null, workflow, NOW, () -> {
            throw new AssertionError("No publication number should have been drawn");
        });

        return request;
    }
}
