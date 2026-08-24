package com.polakowski.requestmanagement.infrastructure.rest;

import com.polakowski.requestmanagement.application.RequestManagement;
import com.polakowski.requestmanagement.application.command.ApplyActionCommand;
import com.polakowski.requestmanagement.application.command.ChangeRequestBodyCommand;
import com.polakowski.requestmanagement.application.command.CreateRequestCommand;
import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.infrastructure.config.ApiProperties;
import com.polakowski.requestmanagement.infrastructure.rest.dto.CreateRequestPayload;
import com.polakowski.requestmanagement.infrastructure.rest.dto.PageResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.ReasonPayload;
import com.polakowski.requestmanagement.infrastructure.rest.dto.RequestResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.RequestSummaryResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.StateChangeResponse;
import com.polakowski.requestmanagement.infrastructure.rest.dto.UpdateBodyPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Inbound adapter exposing the request lifecycle over HTTP.
 *
 * <p>Every lifecycle endpoint is a three line delegation to the same use case: the action to apply
 * is the only thing that differs, because deciding whether it is legal belongs to the domain.
 */
@RestController
@RequestMapping("/api/v1/requests")
@Validated
@RequiredArgsConstructor
@Tag(name = "Requests", description = "Lifecycle of requests")
public class RequestController {

    private final RequestManagement requests;
    private final RequestResponseAssembler assembler;
    private final ApiProperties apiProperties;

    @PostMapping
    @Operation(summary = "Create a request", description = "The name and the body are mandatory.")
    public ResponseEntity<RequestResponse> create(
            @Valid @RequestBody CreateRequestPayload payload,
            UriComponentsBuilder uriBuilder) {
        CreateRequestCommand command = new CreateRequestCommand(payload.name(), payload.body());
        Request created = requests.create(command);

        URI location = uriBuilder
                .path("/api/v1/requests/{id}")
                .buildAndExpand(created.id().value())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(assembler.toResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single request")
    public RequestResponse getById(@PathVariable UUID id) {
        Request request = requests.get(RequestId.of(id));

        return assembler.toResponse(request);
    }

    @GetMapping
    @Operation(
            summary = "Browse requests",
            description = "Paginated and optionally filtered by name fragment and by state.")
    public PageResponse<RequestSummaryResponse> browse(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) RequestState state,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(required = false) Integer size) {
        PageQuery pageQuery = PageQuery.of(page, apiProperties.resolvePageSize(size));
        RequestSearchCriteria criteria = RequestSearchCriteria.of(name, state);
        PageResult<Request> result = requests.browse(criteria, pageQuery);

        return assembler.toPage(result, assembler::toSummary);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Read the audit trail of a request")
    public List<StateChangeResponse> history(@PathVariable UUID id) {
        Request request = requests.get(RequestId.of(id));

        return assembler.toHistory(request);
    }

    @PutMapping("/{id}/body")
    @Operation(
            summary = "Replace the body of a request",
            description = "Only possible while the request is in a state that allows editing.")
    public RequestResponse updateBody(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBodyPayload payload) {
        ChangeRequestBodyCommand command = new ChangeRequestBodyCommand(RequestId.of(id), payload.body());
        Request updated = requests.changeBody(command);

        return assembler.toResponse(updated);
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify a request")
    public RequestResponse verify(@PathVariable UUID id) {
        return applyAction(id, RequestAction.VERIFY, null);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a request")
    public RequestResponse accept(@PathVariable UUID id) {
        return applyAction(id, RequestAction.ACCEPT, null);
    }

    @PostMapping("/{id}/publish")
    @Operation(
            summary = "Publish a request",
            description = "Assigns the unique numeric publication identifier.")
    public RequestResponse publish(@PathVariable UUID id) {
        return applyAction(id, RequestAction.PUBLISH, null);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a request", description = "A reason is mandatory.")
    public RequestResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody ReasonPayload payload) {
        return applyAction(id, RequestAction.REJECT, payload.reason());
    }

    @PostMapping("/{id}/delete")
    @Operation(
            summary = "Delete a request",
            description = "A reason is mandatory. The request moves to DELETED and stays auditable.")
    public RequestResponse delete(
            @PathVariable UUID id,
            @Valid @RequestBody ReasonPayload payload) {
        return applyAction(id, RequestAction.DELETE, payload.reason());
    }

    private RequestResponse applyAction(UUID id, RequestAction action, String reason) {
        ApplyActionCommand command = new ApplyActionCommand(RequestId.of(id), action, reason);
        Request updated = requests.apply(command);

        return assembler.toResponse(updated);
    }
}
