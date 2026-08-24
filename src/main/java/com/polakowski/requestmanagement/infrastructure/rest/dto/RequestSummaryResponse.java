package com.polakowski.requestmanagement.infrastructure.rest.dto;

import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.time.Instant;
import java.util.UUID;

/** Representation used when browsing, deliberately without the body. */
public record RequestSummaryResponse(
        UUID id,
        String name,
        RequestState state,
        Long publicationNumber,
        Instant createdAt,
        Instant lastModifiedAt) {}
