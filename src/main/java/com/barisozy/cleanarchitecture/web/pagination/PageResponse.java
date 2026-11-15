package com.barisozy.cleanarchitecture.web.pagination;

import com.barisozy.cleanarchitecture.application.PageResult;
import java.util.List;
import java.util.function.Function;

/**
 * A page of results as the API exposes it.
 *
 * <p>The record deliberately does not depend on Spring Data: it is the response contract, and the
 * web layer translates an application page into it.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {

    public PageResponse {
        items = List.copyOf(items);
    }

    /** Maps an application page onto the API representation in one step. */
    public static <T, R> PageResponse<R> from(PageResult<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasNext());
    }
}
