package com.bachelorchef.exception;

import com.bachelorchef.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes {@link ApiError} bodies from filters/handlers that run outside Spring MVC. */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static ApiError build(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), code, message, fieldErrors, MDC.get(TraceIdFilter.MDC_KEY));
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), build(status, code, message, Map.of()));
    }
}
