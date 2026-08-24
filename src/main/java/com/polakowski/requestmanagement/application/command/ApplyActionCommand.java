package com.polakowski.requestmanagement.application.command;

import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;

/**
 * Intent to move a request along its lifecycle.
 *
 * @param reason business justification, mandatory only for the transitions that declare it
 */
public record ApplyActionCommand(RequestId requestId, RequestAction action, String reason) {

    public static ApplyActionCommand withoutReason(RequestId requestId, RequestAction action) {
        return new ApplyActionCommand(requestId, action, null);
    }
}
