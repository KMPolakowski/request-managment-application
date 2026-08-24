package com.polakowski.requestmanagement.application.command;

/**
 * Intent to open a new request. Both fields are mandatory; the aggregate is what enforces it.
 */
public record CreateRequestCommand(String name, String body) {}
