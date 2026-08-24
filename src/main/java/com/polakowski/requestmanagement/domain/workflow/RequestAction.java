package com.polakowski.requestmanagement.domain.workflow;

/**
 * The actions that may be requested on a request in order to move it to another state.
 *
 * <p>An action is only an intent: whether it is permitted, and where it leads, is decided by the
 * {@link RequestWorkflow} for the current state of the request.
 */
public enum RequestAction {
    VERIFY,
    ACCEPT,
    REJECT,
    DELETE,
    PUBLISH
}
