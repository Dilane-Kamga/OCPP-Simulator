package com.accenture.nexcharge.simulator.config;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A simple {@link Pageable} implementation that passes a raw SQL offset and limit
 * directly to Spring Data JPA (which forwards them to {@code setFirstResult} /
 * {@code setMaxResults} on the underlying JPA query).
 *
 * <p>This avoids the "page * size" arithmetic of {@link org.springframework.data.domain.PageRequest}
 * and lets callers specify an arbitrary row offset independently of the page size.</p>
 */
public class OffsetLimitPageable implements Pageable {

    private final long offset;
    private final int limit;

    public OffsetLimitPageable(long offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("Offset must be >= 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be >= 1");
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    @Override
    public Pageable next() {
        return new OffsetLimitPageable(offset + limit, limit);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetLimitPageable(Math.max(0, offset - limit), limit) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetLimitPageable(0, limit);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetLimitPageable((long) pageNumber * limit, limit);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
