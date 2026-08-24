package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Persistence view of one audit trail entry. Rows are never updated, only appended. */
@Entity
@Table(name = "request_state_change")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StateChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private RequestEntity request;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 32, updatable = false)
    private RequestState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 32, updatable = false)
    private RequestState toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 32, updatable = false)
    private RequestAction action;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    StateChangeEntity(
            int sequenceNumber,
            RequestState fromState,
            RequestState toState,
            RequestAction action,
            String reason,
            Instant occurredAt) {
        this.sequenceNumber = sequenceNumber;
        this.fromState = fromState;
        this.toState = toState;
        this.action = action;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    void assignTo(RequestEntity request) {
        this.request = request;
    }
}
