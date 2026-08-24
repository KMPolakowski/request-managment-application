package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.workflow.RequestState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistence view of a request. It carries no behaviour on purpose: every rule lives in the
 * domain aggregate, this class only knows how rows are shaped.
 *
 * <p>Accessors are generated and package private, so the mapping stays an internal affair of this
 * package rather than an API the rest of the application could reach for.
 */
@Entity
@Table(name = "request")
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "body", nullable = false, length = 10_000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private RequestState state;

    @Column(name = "publication_number", unique = true)
    private Long publicationNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    @Setter(AccessLevel.NONE)
    private List<StateChangeEntity> history = new ArrayList<>();

    RequestEntity(UUID id) {
        this.id = id;
    }

    void addStateChange(StateChangeEntity stateChange) {
        stateChange.assignTo(this);
        history.add(stateChange);
    }
}
