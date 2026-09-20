package com.eofp.order.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Error envelope from docs/06 section 1.2. */
public record ApiErrorResponse(ErrorBody error, Instant timestamp, String path, String traceId) {

    public record ErrorBody(String code, String message,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldIssue> details) {
    }

    public record FieldIssue(String field, String issue) {
    }
}
