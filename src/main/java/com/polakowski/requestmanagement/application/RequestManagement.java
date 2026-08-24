package com.polakowski.requestmanagement.application;

import com.polakowski.requestmanagement.application.command.ApplyActionCommand;
import com.polakowski.requestmanagement.application.command.ChangeRequestBodyCommand;
import com.polakowski.requestmanagement.application.command.CreateRequestCommand;
import com.polakowski.requestmanagement.domain.exception.RequestNotFoundException;
import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;

/**
 * Inbound port of the application: everything that can be done with a request.
 *
 * <p>Adapters - the REST controller today, a batch or a message listener tomorrow - depend on this
 * interface only, and never on the implementation or on the persistence layer.
 */
public interface RequestManagement {

    /** Opens a new request in the initial state of the workflow. */
    Request create(CreateRequestCommand command);

    /**
     * Replaces the body of a request.
     *
     * @throws RequestNotFoundException when no such request exists
     */
    Request changeBody(ChangeRequestBodyCommand command);

    /**
     * Moves a request along its lifecycle.
     *
     * @throws RequestNotFoundException when no such request exists
     */
    Request apply(ApplyActionCommand command);

    /**
     * @throws RequestNotFoundException when no such request exists
     */
    Request get(RequestId requestId);

    /** Lists the requests matching the criteria, most recently created first. */
    PageResult<Request> browse(RequestSearchCriteria criteria, PageQuery page);
}
