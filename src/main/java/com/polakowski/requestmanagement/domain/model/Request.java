package com.polakowski.requestmanagement.domain.model;

import com.polakowski.requestmanagement.domain.exception.ContentNotEditableException;
import com.polakowski.requestmanagement.domain.exception.InvalidRequestContentException;
import com.polakowski.requestmanagement.domain.exception.ReasonRequiredException;
import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.domain.workflow.Transition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Aggregate root of the model: a request together with its lifecycle and its audit trail.
 *
 * <p>The aggregate owns its invariants. It never reaches out to infrastructure: the lifecycle rules
 * are handed to it as a {@link RequestWorkflow}, the current time as an {@link Instant} and the
 * publication number as a {@link LongSupplier} that is only consulted when a transition actually
 * assigns one. That keeps the class free of framework, clock and database concerns, and directly
 * unit testable.
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "name", "state", "publicationNumber"})
public final class Request {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 10_000;
    public static final int MAX_REASON_LENGTH = 500;

    private final RequestId id;
    private final String name;
    private final Instant createdAt;

    private String body;
    private RequestState state;
    private Instant lastModifiedAt;

    /** Absent until a transition assigns one; exposed as an {@link Optional} rather than as null. */
    @Getter(AccessLevel.NONE)
    private Long publicationNumber;

    /** Append only, and handed out as a copy, so no caller can rewrite what already happened. */
    @Getter(AccessLevel.NONE)
    private final List<StateChange> history;

    private Request(
            RequestId id,
            String name,
            String body,
            RequestState state,
            Long publicationNumber,
            Instant createdAt,
            Instant lastModifiedAt,
            List<StateChange> history) {
        this.id = id;
        this.name = name;
        this.body = body;
        this.state = state;
        this.publicationNumber = publicationNumber;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
        this.history = new ArrayList<>(history);
    }

    /**
     * Creates a request in the initial state of the workflow. Both a name and a body are mandatory.
     */
    public static Request create(
            RequestId id,
            String name,
            String body,
            RequestWorkflow workflow,
            Instant now) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workflow, "workflow must not be null");
        Objects.requireNonNull(now, "now must not be null");

        String validatedName = validated("name", name, MAX_NAME_LENGTH);
        String validatedBody = validated("body", body, MAX_BODY_LENGTH);
        RequestState initialState = workflow.initialState();
        List<StateChange> openingEntry = List.of(StateChange.creation(initialState, now));

        return new Request(
                id,
                validatedName,
                validatedBody,
                initialState,
                null,
                now,
                now,
                openingEntry);
    }

    /** Restores an aggregate that was previously stored, without replaying its lifecycle. */
    public static Request fromSnapshot(RequestSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        return new Request(
                snapshot.id(),
                snapshot.name(),
                snapshot.body(),
                snapshot.state(),
                snapshot.publicationNumber(),
                snapshot.createdAt(),
                snapshot.lastModifiedAt(),
                snapshot.history());
    }

    /**
     * Replaces the body of the request.
     *
     * @throws ContentNotEditableException when the current state does not allow editing
     */
    public void changeBody(String newBody, RequestWorkflow workflow, Instant now) {
        Objects.requireNonNull(workflow, "workflow must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (!workflow.isContentEditable(state)) {
            throw new ContentNotEditableException(state, workflow.contentEditableStates());
        }

        this.body = validated("body", newBody, MAX_BODY_LENGTH);
        this.lastModifiedAt = now;
    }

    /**
     * Applies an action to the request, moving it along the state diagram and appending an entry to
     * its audit trail.
     *
     * @param publicationNumbers consulted only when the resolved transition assigns a publication
     *                           number, so that no number is burnt on a rejected transition
     * @throws TransitionNotAllowedException when the action is not permitted in the current state
     * @throws ReasonRequiredException       when the transition must be justified and no reason was given
     */
    public void apply(
            RequestAction action,
            String reason,
            RequestWorkflow workflow,
            Instant now,
            LongSupplier publicationNumbers) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(workflow, "workflow must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(publicationNumbers, "publicationNumbers must not be null");

        Transition transition = workflow.transitionFor(state, action);
        String justification = normalisedReason(transition, reason);

        if (transition.assignsPublicationNumber()) {
            this.publicationNumber = publicationNumbers.getAsLong();
        }

        RequestState previousState = this.state;
        this.state = transition.target();
        this.lastModifiedAt = now;
        this.history.add(new StateChange(
                history.size() + 1,
                previousState,
                transition.target(),
                action,
                justification,
                now));
    }

    public RequestSnapshot toSnapshot() {
        return new RequestSnapshot(
                id,
                name,
                body,
                state,
                publicationNumber,
                createdAt,
                lastModifiedAt,
                List.copyOf(history));
    }

    /** The unique numeric identifier, present once the request has been published. */
    public Optional<Long> publicationNumber() {
        return Optional.ofNullable(publicationNumber);
    }

    /** The audit trail, oldest entry first. */
    public List<StateChange> history() {
        return List.copyOf(history);
    }

    private static String normalisedReason(Transition transition, String reason) {
        String trimmed = reason == null ? null : reason.trim();
        boolean supplied = trimmed != null && !trimmed.isEmpty();

        if (transition.reasonRequired() && !supplied) {
            throw new ReasonRequiredException(transition.action());
        }
        if (!supplied) {
            return null;
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new InvalidRequestContentException(
                    "reason",
                    "reason must not exceed %d characters".formatted(MAX_REASON_LENGTH));
        }

        return trimmed;
    }

    private static String validated(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestContentException(field, "%s must not be blank".formatted(field));
        }

        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidRequestContentException(
                    field,
                    "%s must not exceed %d characters".formatted(field, maxLength));
        }

        return trimmed;
    }
}
