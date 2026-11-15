package com.barisozy.cleanarchitecture.application;

import java.util.List;

/**
 * A page of application results.
 *
 * <p>This is the application's own pagination contract. It keeps Spring Data out of the services:
 * the web layer asks for a page index and a size, and the persistence adapter translates those two
 * numbers into whatever its library needs.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
    }

    /** The number of pages needed for the result. */
    public int totalPages() {
        return (int) Math.ceilDiv(totalElements, size);
    }

    /** Whether another page exists behind this one. */
    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }
}
