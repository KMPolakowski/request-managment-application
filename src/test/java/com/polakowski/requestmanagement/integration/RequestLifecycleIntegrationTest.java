package com.polakowski.requestmanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
 * End to end coverage over the real stack: HTTP, application service, JPA, Flyway managed schema
 * and the database sequence behind the publication number.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("The service, end to end")
class RequestLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("takes a request from creation to publication and keeps the whole trail")
    void takesARequestFromCreationToPublication() throws Exception {
        UUID id = createRequest("Liquidity coverage ratio report", "First draft");

        mockMvc.perform(put("/api/v1/requests/{id}/body", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Reviewed draft"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Reviewed draft"));

        mockMvc.perform(post("/api/v1/requests/{id}/verify", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VERIFIED"));

        mockMvc.perform(post("/api/v1/requests/{id}/accept", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.bodyEditable").value(false));

        mockMvc.perform(post("/api/v1/requests/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.publicationNumber").isNumber())
                .andExpect(jsonPath("$.allowedActions").isEmpty());

        mockMvc.perform(get("/api/v1/requests/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].to").value(
                        org.hamcrest.Matchers.contains("CREATED", "VERIFIED", "ACCEPTED", "PUBLISHED")));
    }

    @Test
    @DisplayName("gives each published request its own number")
    void givesEachPublishedRequestItsOwnNumber() throws Exception {
        long first = publish(createRequest("First to publish", "Content"));
        long second = publish(createRequest("Second to publish", "Content"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("refuses to publish a request that was never accepted")
    void refusesToPublishARequestThatWasNeverAccepted() throws Exception {
        UUID id = createRequest("Never accepted", "Content");

        mockMvc.perform(post("/api/v1/requests/{id}/publish", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentState").value("CREATED"));

        mockMvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.publicationNumber").doesNotExist());
    }

    @Test
    @DisplayName("refuses to delete without a reason and keeps the request alive")
    void refusesToDeleteWithoutAReason() throws Exception {
        UUID id = createRequest("Deletion candidate", "Content");

        mockMvc.perform(post("/api/v1/requests/{id}/delete", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(jsonPath("$.state").value("CREATED"));
    }

    @Test
    @DisplayName("keeps a deleted request readable, with the reason it was deleted for")
    void keepsADeletedRequestReadable() throws Exception {
        UUID id = createRequest("Submitted twice", "Content");

        mockMvc.perform(post("/api/v1/requests/{id}/delete", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Submitted twice by mistake"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DELETED"));

        mockMvc.perform(get("/api/v1/requests/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].reason").value("Submitted twice by mistake"))
                .andExpect(jsonPath("$[1].action").value("DELETE"));
    }

    @Test
    @DisplayName("refuses to edit the body of an accepted request")
    void refusesToEditTheBodyOfAnAcceptedRequest() throws Exception {
        UUID id = createRequest("Frozen content", "Content");
        mockMvc.perform(post("/api/v1/requests/{id}/verify", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/accept", id)).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/requests/{id}/body", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Too late"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Body is not editable"));
    }

    @Test
    @DisplayName("browses with the default page size and the requested filters")
    void browsesWithFiltersAndPagination() throws Exception {
        String marker = "Browsing " + UUID.randomUUID();
        for (int index = 0; index < 3; index++) {
            createRequest(marker + " " + index, "Content");
        }

        mockMvc.perform(get("/api/v1/requests").param("name", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3));

        mockMvc.perform(get("/api/v1/requests")
                        .param("name", marker)
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/v1/requests")
                        .param("name", marker)
                        .param("state", "PUBLISHED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("answers 404 for a request that does not exist")
    void answersNotFoundForAnUnknownRequest() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:request-management:problem:request-not-found"));
    }

    private UUID createRequest(String name, String body) throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("name", name);
            put("body", body);
        }});
        String response = mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private long publish(UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/verify", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/accept", id)).andExpect(status().isOk());
        String response = mockMvc.perform(post("/api/v1/requests/{id}/publish", id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("publicationNumber").asLong();
    }
}
