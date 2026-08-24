package com.polakowski.requestmanagement.infrastructure.rest.dto;

import com.polakowski.requestmanagement.domain.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the calls that must be justified, namely deletion and rejection. */
public record ReasonPayload(
        @NotBlank(message = "reason is mandatory")
        @Size(max = Request.MAX_REASON_LENGTH, message = "reason must not exceed {max} characters")
        String reason) {}
