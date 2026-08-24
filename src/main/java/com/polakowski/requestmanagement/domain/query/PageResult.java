package com.polakowski.requestmanagement.domain.query;

import java.util.List;
import java.util.function.Function;

/**
 * A slice of results together with the paging metadata needed to navigate the rest.
 *
 * @param content       elements of this page
 * @param page          zero based index of this page
 * @param size          requested page size
 * @param totalElements total number of elements matching the query
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

    /** Applies a mapping to every element while preserving the paging metadata. */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements);
    }
}
