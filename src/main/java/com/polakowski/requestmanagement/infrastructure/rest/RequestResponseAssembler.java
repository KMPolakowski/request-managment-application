package com.polakowski.requestmanagement.infrastructure.rest;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.infrastructure.rest.dto.PageResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.RequestResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.RequestSummaryResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.StateChangeResponse;
import java.util.List;
import java.util.function.Function;

/**
 * Builds the representations returned by the API.
 *
 * <p>The controller depends on this interface, not on the class behind it, so how a request is
 * rendered can change - a leaner payload, hypermedia links - without the controller noticing.
 */
public interface RequestResponseAssembler {

    /** The full representation, including the body. */
    RequestResponse toResponse(Request request);

    /** The lighter representation used when browsing. */
    RequestSummaryResponse toSummary(Request request);

    /** The audit trail, oldest entry first. */
    List<StateChangeResponse> toHistory(Request request);

    /** Wraps a page of requests, mapped by {@code mapper}, together with its navigation metadata. */
    <T> PageResponse<T> toPage(PageResult<Request> result, Function<Request, T> mapper);
}
