package com.stockpilot.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for expected, client-facing failures. The message is safe to show to users.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
