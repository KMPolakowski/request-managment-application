package com.polakowski.requestmanagement.application;

import com.polakowski.requestmanagement.application.command.ApplyActionCommand;
import com.polakowski.requestmanagement.application.command.ChangeRequestBodyCommand;
import com.polakowski.requestmanagement.application.command.CreateRequestCommand;
import com.polakowski.requestmanagement.domain.exception.RequestNotFoundException;
import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.port.PublicationNumberGenerator;
import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import com.polakowski.requestmanagement.domain.port.RequestRepository;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the use cases: it loads the aggregate, lets the aggregate decide, and stores the
 * outcome. No business rule lives here - rules belong to {@link Request} and to the
 * {@link RequestWorkflow} it is given.
 *
 * <p>Every collaborator arrives through the constructor and every one of them is an abstraction:
 * the repository, the two generators and the workflow are interfaces or immutable domain objects,
 * and even the current time is injected. Nothing in this class knows which implementation it is
 * talking to, which is what lets the same orchestration run against a database or against a map.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RequestService implements RequestManagement {

    private final RequestRepository requests;
    private final RequestWorkflow workflow;
    private final RequestIdGenerator requestIds;
    private final PublicationNumberGenerator publicationNumbers;
    private final Clock clock;

    @Override
    public Request create(CreateRequestCommand command) {
        Request request = Request.create(
                requestIds.next(),
                command.name(),
                command.body(),
                workflow,
                now());

        Request saved = requests.save(request);
        log.info("Created request {} in state {}", saved.id(), saved.state());

        return saved;
    }

    @Override
    public Request changeBody(ChangeRequestBodyCommand command) {
        Request request = load(command.requestId());
        request.changeBody(command.body(), workflow, now());

        Request saved = requests.save(request);
        log.info("Updated the body of request {}", saved.id());

        return saved;
    }

    @Override
    public Request apply(ApplyActionCommand command) {
        Request request = load(command.requestId());
        request.apply(
                command.action(),
                command.reason(),
                workflow,
                now(),
                publicationNumbers::next);

        Request saved = requests.save(request);
        log.info(
                "Applied {} to request {}, now in state {}",
                command.action(),
                saved.id(),
                saved.state());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Request get(RequestId requestId) {
        return load(requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Request> browse(RequestSearchCriteria criteria, PageQuery page) {
        return requests.search(criteria, page);
    }

    private Request load(RequestId requestId) {
        return requests
                .findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException(requestId));
    }

    private Instant now() {
        return clock.instant();
    }
}
