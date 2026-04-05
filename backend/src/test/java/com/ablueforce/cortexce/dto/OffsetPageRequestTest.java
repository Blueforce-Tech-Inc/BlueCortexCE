package com.ablueforce.cortexce.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OffsetPageRequestTest {

    @Test
    void constructor_throwsOnNegativeSize() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new OffsetPageRequest(0, -1, 0));
        assertEquals("Page size must not be negative", ex.getMessage());
    }

    @Test
    void constructor_acceptsZeroSize() {
        OffsetPageRequest req = new OffsetPageRequest(0, 0, 0);
        assertEquals(0, req.getPageSize());
        assertEquals(0, req.getOffset());
    }

    @Test
    void next_incrementsPageAndOffset() {
        OffsetPageRequest req = new OffsetPageRequest(2, 20, 40);
        Pageable next = req.next();
        assertEquals(3, next.getPageNumber());
        assertEquals(20, next.getPageSize());
        assertEquals(60, next.getOffset());
    }

    @Test
    void previousOrFirst_whenHasPrevious_returnsPrevious() {
        OffsetPageRequest req = new OffsetPageRequest(2, 20, 40);
        Pageable prev = req.previousOrFirst();
        assertEquals(1, prev.getPageNumber());
        assertEquals(20, prev.getPageSize());
        assertEquals(20, prev.getOffset());
    }

    @Test
    void previousOrFirst_whenNoPrevious_returnsFirst() {
        OffsetPageRequest req = new OffsetPageRequest(0, 20, 0);
        Pageable result = req.previousOrFirst();
        assertEquals(0, result.getPageNumber());
        assertEquals(0, result.getOffset());
    }

    @Test
    void previousOrFirst_neverReturnsNegativeOffset() {
        // Edge case: page 1 but offset < size (should still go to offset 0)
        OffsetPageRequest req = new OffsetPageRequest(1, 20, 5);
        Pageable prev = req.previousOrFirst();
        assertEquals(0, prev.getOffset());
    }

    @Test
    void first_alwaysReturnsZeroPageAndOffset() {
        OffsetPageRequest req = new OffsetPageRequest(5, 20, 100);
        Pageable first = req.first();
        assertEquals(0, first.getPageNumber());
        assertEquals(0, first.getOffset());
        assertEquals(20, first.getPageSize());
    }

    @Test
    void withPage_preservesOriginalOffset() {
        OffsetPageRequest req = new OffsetPageRequest(0, 20, 50);
        Pageable p5 = req.withPage(5);
        assertEquals(5, p5.getPageNumber());
        assertEquals(50, p5.getOffset()); // offset unchanged
        assertEquals(20, p5.getPageSize());
    }

    @Test
    void hasPrevious_trueWhenPageGreaterThanZero() {
        assertTrue(new OffsetPageRequest(1, 20, 20).hasPrevious());
        assertFalse(new OffsetPageRequest(0, 20, 0).hasPrevious());
    }

    @Test
    void withSort_returnsNewInstanceWithSort() {
        OffsetPageRequest req = new OffsetPageRequest(0, 20, 0);
        OffsetPageRequest sorted = req.withSort(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), sorted.getSort());
        // Original unchanged
        assertEquals(Sort.unsorted(), req.getSort());
    }

    @Test
    void equals_andHashCode_contract() {
        OffsetPageRequest a = new OffsetPageRequest(1, 20, 40, Sort.by("name"));
        OffsetPageRequest b = new OffsetPageRequest(1, 20, 40, Sort.by("name"));
        OffsetPageRequest c = new OffsetPageRequest(1, 20, 41, Sort.by("name"));
        OffsetPageRequest d = new OffsetPageRequest(1, 20, 40, Sort.by("createdAt"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, "not a pageable");
        assertNotEquals(a, null);
    }

    @Test
    void equals_sameInstance() {
        OffsetPageRequest req = new OffsetPageRequest(1, 20, 40);
        assertEquals(req, req);
    }

    @Test
    void hashCode_setContains() {
        Set<OffsetPageRequest> set = new HashSet<>();
        OffsetPageRequest r1 = new OffsetPageRequest(1, 20, 40);
        OffsetPageRequest r2 = new OffsetPageRequest(1, 20, 40);
        OffsetPageRequest r3 = new OffsetPageRequest(2, 20, 40);
        set.add(r1);
        assertTrue(set.contains(r2)); // equals
        assertFalse(set.contains(r3)); // different offset
    }

    @Test
    void toString_containsAllFields() {
        String str = new OffsetPageRequest(1, 20, 40, Sort.unsorted()).toString();
        assertTrue(str.contains("page=1"));
        assertTrue(str.contains("size=20"));
        assertTrue(str.contains("offset=40"));
    }

    @Test
    void isPaged_alwaysTrue() {
        assertTrue(new OffsetPageRequest(0, 10, 0).isPaged());
    }

    @Test
    void isUnpaged_alwaysFalse() {
        assertFalse(new OffsetPageRequest(0, 10, 0).isUnpaged());
    }
}
