package com.polakowski.requestmanagement.domain.model;

import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.time.Instant;
import java.util.List;

/**
 * Flat, immutable view of a {@link Request}.
 *
 * <p>Adapters read and write aggregates through this record instead of through setters or
 * reflection, which lets the aggregate keep full control over its own invariants while persistence
 * still gets everything it needs.
 */
public record RequestSnapshot(
        RequestId id,
        String name,
        String body,
        RequestState state,
        Long publicationNumber,
        Instant createdAt,
        Instant lastModifiedAt,
        List<StateChange> history) {

    public RequestSnapshot {
        history = List.copyOf(history);
    }
}
