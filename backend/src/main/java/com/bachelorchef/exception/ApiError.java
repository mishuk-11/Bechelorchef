package com.bachelorchef.exception;

import java.time.Instant;
import java.util.Map;

/** The single error shape returned by every endpoint. */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        String traceId) {
}
