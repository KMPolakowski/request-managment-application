package com.polakowski.requestmanagement.application.command;

import com.polakowski.requestmanagement.domain.model.RequestId;

/** Intent to replace the body of an existing request. */
public record ChangeRequestBodyCommand(RequestId requestId, String body) {}
