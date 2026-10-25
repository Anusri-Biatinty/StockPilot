package com.stockpilot.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is valid but conflicts with the current state (duplicates, invalid status transitions, ...).
 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
