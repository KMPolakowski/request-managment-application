package com.polakowski.requestmanagement.domain.query;

/**
 * Zero based pagination request, expressed in domain terms so that neither the application nor the
 * domain has to depend on a persistence framework's paging types.
 *
 * @param page zero based page index
 * @param size number of elements per page
 */
public record PageQuery(int page, int size) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative but was " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive but was " + size);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
