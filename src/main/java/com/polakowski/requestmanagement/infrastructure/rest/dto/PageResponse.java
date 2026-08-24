package com.polakowski.requestmanagement.infrastructure.rest.dto;

import java.util.List;

/** Envelope carrying a page of results together with its navigation metadata. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {}
