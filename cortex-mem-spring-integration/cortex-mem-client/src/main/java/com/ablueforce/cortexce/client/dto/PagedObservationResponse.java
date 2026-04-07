package com.ablueforce.cortexce.client.dto;

import java.util.List;

/**
 * Paginated observation response from {@code GET /api/observations}.
 *
 * @param items list of observations for the current page
 * @param hasMore true if there are more pages available
 */
public record PagedObservationResponse(
    List<ObservationResponse> items,
    boolean hasMore
) {}
