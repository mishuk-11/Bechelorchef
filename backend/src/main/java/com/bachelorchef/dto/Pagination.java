package com.bachelorchef.dto;

import com.bachelorchef.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Validates paging parameters and maps public sort keys through a server-side allow-list. */
public final class Pagination {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 12;

    private Pagination() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        Map<String, String> errors = new LinkedHashMap<>();
        if (p < 0) {
            errors.put("page", "Must be 0 or greater.");
        }
        if (s < 1 || s > MAX_SIZE) {
            errors.put("size", "Must be between 1 and " + MAX_SIZE + ".");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return PageRequest.of(p, s, sort);
    }

    /** Looks up a sort key in the allow-list; unknown keys are a validation error, never passed to SQL. */
    public static Sort sort(String key, Map<String, Sort> allowed, String defaultKey) {
        String k = key == null || key.isBlank() ? defaultKey : key;
        Sort sort = allowed.get(k);
        if (sort == null) {
            throw ApiException.validation("sort", "Must be one of " + String.join(", ", allowed.keySet()) + ".");
        }
        return sort;
    }
}
