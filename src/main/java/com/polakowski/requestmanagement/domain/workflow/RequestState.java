package com.polakowski.requestmanagement.domain.workflow;

/**
 * The states a request can be in, as defined by the state diagram.
 *
 * <p>Which of these states are reachable, and how, is not encoded here: it is described by a
 * {@link WorkflowDefinition} so that the lifecycle can be changed through configuration rather
 * than through code.
 */
public enum RequestState {
    CREATED,
    VERIFIED,
    ACCEPTED,
    PUBLISHED,
    REJECTED,
    DELETED
}
