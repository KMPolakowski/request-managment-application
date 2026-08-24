package com.polakowski.requestmanagement.infrastructure.rest;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.StateChange;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.infrastructure.rest.dto.PageResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.RequestResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.RequestSummaryResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.StateChangeResponse;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Renders requests as payloads.
 *
 * <p>It also tells the caller which actions are currently possible, which it reads from the
 * injected workflow rather than from a hard-coded table, so a change of lifecycle is immediately
 * visible in the API.
 */
@Component
@RequiredArgsConstructor
public class DefaultRequestResponseAssembler implements RequestResponseAssembler {

    private final RequestWorkflow workflow;

    @Override
    public RequestResponse toResponse(Request request) {
        return new RequestResponse(
                request.id().value(),
                request.name(),
                request.body(),
                request.state(),
                request.publicationNumber().orElse(null),
                request.createdAt(),
                request.lastModifiedAt(),
                sortedActions(request),
                workflow.isContentEditable(request.state()));
    }

    @Override
    public RequestSummaryResponse toSummary(Request request) {
        return new RequestSummaryResponse(
                request.id().value(),
                request.name(),
                request.state(),
                request.publicationNumber().orElse(null),
                request.createdAt(),
                request.lastModifiedAt());
    }

    @Override
    public List<StateChangeResponse> toHistory(Request request) {
        return request.history().stream()
                .map(DefaultRequestResponseAssembler::toResponse)
                .toList();
    }

    @Override
    public <T> PageResponse<T> toPage(PageResult<Request> result, Function<Request, T> mapper) {
        List<T> content = result.content().stream()
                .map(mapper)
                .toList();

        return new PageResponse<>(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext());
    }

    private List<RequestAction> sortedActions(Request request) {
        return workflow.allowedActions(request.state()).stream()
                .sorted(Comparator.comparing(RequestAction::name))
                .toList();
    }

    private static StateChangeResponse toResponse(StateChange change) {
        return new StateChangeResponse(
                change.sequenceNumber(),
                change.from(),
                change.to(),
                change.action(),
                change.reason(),
                change.occurredAt());
    }
}
