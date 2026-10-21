package com.certitrack.util;

import com.certitrack.exception.DomainException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;

public final class PageRequests {
    private PageRequests() {}

    public static Pageable safe(Pageable request) {
        // Do not let caller-provided sort paths traverse private entity relationships.
        if (request.getSort().stream().anyMatch(order -> !order.getProperty().equals("id")))
            throw new DomainException(HttpStatus.BAD_REQUEST, "Only sorting by id is supported");
        Sort sort = request.getSort().isSorted() ? request.getSort() : Sort.by("id").descending();
        return PageRequest.of(request.getPageNumber(), Math.min(request.getPageSize(), 100), sort);
    }
}
