package com.polakowski.requestmanagement.infrastructure.rest.dto;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.time.Instant;

/** One entry of the audit trail. */
public record StateChangeResponse(
        int sequenceNumber,
        RequestState from,
        RequestState to,
        RequestAction action,
        String reason,
        Instant occurredAt) {}
