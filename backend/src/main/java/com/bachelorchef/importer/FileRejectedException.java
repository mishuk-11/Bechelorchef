package com.bachelorchef.importer;

import org.springframework.http.HttpStatus;

/** The whole file is unusable (empty, too large, malformed, wrong type, too many rows, bad header). */
public class FileRejectedException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public FileRejectedException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public FileRejectedException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
