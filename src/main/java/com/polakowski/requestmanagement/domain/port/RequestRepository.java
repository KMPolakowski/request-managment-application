package com.polakowski.requestmanagement.domain.port;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import java.util.Optional;

/**
 * Outbound port giving the domain access to stored requests.
 *
 * <p>Note that there is no delete operation: deleting a request is a state transition of its
 * lifecycle, not the removal of a row, which is what keeps the audit trail complete.
 */
public interface RequestRepository {

    /** Inserts or updates the aggregate and returns the stored state. */
    Request save(Request request);

    Optional<Request> findById(RequestId id);

    /** Returns the requests matching the criteria, most recently created first. */
    PageResult<Request> search(RequestSearchCriteria criteria, PageQuery page);
}
