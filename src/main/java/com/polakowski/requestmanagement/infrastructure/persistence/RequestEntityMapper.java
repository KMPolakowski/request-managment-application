package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.model.Request;

/**
 * Translates between the aggregate and its rows.
 *
 * <p>An interface rather than a static utility, so that {@link JpaRequestRepository} depends on the
 * idea of mapping instead of on one particular implementation of it, and receives it through its
 * constructor like every other collaborator.
 */
interface RequestEntityMapper {

    /** Builds the rows of a request that has never been stored. */
    RequestEntity toNewEntity(Request request);

    /** Copies the mutable state of an already stored request onto its rows. */
    void updateEntity(RequestEntity entity, Request request);

    /** Rebuilds the aggregate from its rows. */
    Request toDomain(RequestEntity entity);
}
