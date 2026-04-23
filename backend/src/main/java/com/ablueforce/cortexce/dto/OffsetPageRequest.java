package com.ablueforce.cortexce.dto;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.Serializable;

/**
 * A Pageable implementation that supports a true byte offset within the result set.
 *
 * Standard Spring Data {@link org.springframework.data.domain.PageRequest} calculates
 * offset as {@code page * size}, which means when offset < size, all requests map to page 0.
 * This class allows specifying offset independently of page index.
 *
 * JPA translates this to: {@code LIMIT size OFFSET offset}, enabling correct pagination
 * when offset (e.g., 5) is less than limit (e.g., 20).
 *
 * Used by ViewerController pagination endpoints where offset can be any value (e.g., 5)
 * and limit can be larger (e.g., 20), requiring true offset-based pagination.
 */
public class OffsetPageRequest implements Pageable, Serializable {

    private static final long serialVersionUID = 1L;

    private final int page;
    private final int size;
    private final long offset;
    private final Sort sort;

    public OffsetPageRequest(int page, int size, long offset) {
        this(page, size, offset, Sort.unsorted());
    }

    public OffsetPageRequest(int page, int size, long offset, Sort sort) {
        if (size < 0) throw new IllegalArgumentException("Page size must not be negative");
        this.page = page;
        this.size = size;
        this.offset = offset;
        this.sort = sort != null ? sort : Sort.unsorted();
    }

    @Override
    public int getPageNumber() {
        return page;
    }

    @Override
    public int getPageSize() {
        return size;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(page + 1, size, offset + size, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious()
            ? new OffsetPageRequest(page - 1, size, Math.max(0, offset - size), sort)
            : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, size, 0, sort);
    }

    @Override
    public boolean hasPrevious() {
        return page > 0;
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageRequest(pageNumber, size, (long) pageNumber * size, sort);
    }

    @Override
    public boolean isPaged() {
        return true;
    }

    @Override
    public boolean isUnpaged() {
        return false;
    }

    /**
     * Returns a new OffsetPageRequest with the specified sort.
     */
    public OffsetPageRequest withSort(Sort sort) {
        return new OffsetPageRequest(page, size, offset, sort);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pageable that)) return false;
        return page == that.getPageNumber()
            && size == that.getPageSize()
            && offset == that.getOffset()
            && sort.equals(that.getSort());
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + page;
        result = 31 * result + size;
        result = 31 * result + Long.hashCode(offset);
        result = 31 * result + sort.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("OffsetPageRequest [page=%d, size=%d, offset=%d, sort=%s]",
            page, size, offset, sort);
    }
}
