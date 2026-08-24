package com.polakowski.requestmanagement.infrastructure.rest.dto;

import com.polakowski.requestmanagement.domain.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a request creation call. */
public record CreateRequestPayload(
        @NotBlank(message = "name is mandatory")
        @Size(max = Request.MAX_NAME_LENGTH, message = "name must not exceed {max} characters")
        String name,

        @NotBlank(message = "body is mandatory")
        @Size(max = Request.MAX_BODY_LENGTH, message = "body must not exceed {max} characters")
        String body) {}
