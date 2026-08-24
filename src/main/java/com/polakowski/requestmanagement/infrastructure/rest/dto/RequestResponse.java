package com.polakowski.requestmanagement.infrastructure.rest.dto;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full representation of a request.
 *
 * @param publicationNumber unique numeric identifier, present once the request has been published
 * @param allowedActions    actions the caller may apply next, derived from the configured workflow
 * @param bodyEditable      whether the body may still be modified in the current state
 */
public record RequestResponse(
        UUID id,
        String name,
        String body,
        RequestState state,
        Long publicationNumber,
        Instant createdAt,
        Instant lastModifiedAt,
        List<RequestAction> allowedActions,
        boolean bodyEditable) {}
