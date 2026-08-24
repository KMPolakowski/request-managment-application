package com.polakowski.requestmanagement.infrastructure.rest.dto;

import com.polakowski.requestmanagement.domain.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a content update call. */
public record UpdateBodyPayload(
        @NotBlank(message = "body is mandatory")
        @Size(max = Request.MAX_BODY_LENGTH, message = "body must not exceed {max} characters")
        String body) {}
