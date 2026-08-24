package com.polakowski.requestmanagement.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves that the lifecycle really is configuration and not code: the same binaries run a shorter
 * workflow, in which a request is published straight after verification and can no longer be
 * edited once verified.
 */
@SpringBootTest(properties = {
        "request-management.workflow.initial-state=CREATED",
        "request-management.workflow.content-editable-states[0]=CREATED",
        "request-management.workflow.transitions[0].source=CREATED",
        "request-management.workflow.transitions[0].action=VERIFY",
        "request-management.workflow.transitions[0].target=VERIFIED",
        "request-management.workflow.transitions[1].source=VERIFIED",
        "request-management.workflow.transitions[1].action=PUBLISH",
        "request-management.workflow.transitions[1].target=PUBLISHED",
        "request-management.workflow.transitions[1].assigns-publication-number=true",
        "request-management.workflow.transitions[2].source=CREATED",
        "request-management.workflow.transitions[2].action=DELETE",
        "request-management.workflow.transitions[2].target=DELETED",
        "request-management.workflow.transitions[2].reason-required=true"
})
@AutoConfigureMockMvc
@DisplayName("The service running a configured lifecycle")
class ConfigurableWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("publishes straight after verification, without an acceptance step")
    void publishesStraightAfterVerification() throws Exception {
        UUID id = createRequest();

        mockMvc.perform(post("/api/v1/requests/{id}/verify", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VERIFIED"))
                .andExpect(jsonPath("$.bodyEditable").value(false))
                .andExpect(jsonPath("$.allowedActions").value(
                        org.hamcrest.Matchers.contains("PUBLISH")));

        mockMvc.perform(post("/api/v1/requests/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.publicationNumber").isNumber());
    }

    @Test
    @DisplayName("no longer allows the acceptance step that the default lifecycle has")
    void noLongerAllowsAcceptance() throws Exception {
        UUID id = createRequest();
        mockMvc.perform(post("/api/v1/requests/{id}/verify", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/accept", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.contains("PUBLISH")));
    }

    private UUID createRequest() throws Exception {
        String response = mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Shortened lifecycle", "body": "Content"}"""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
