package com.stockpilot.exception;

public class InvalidStatusTransitionException extends ConflictException {

    public InvalidStatusTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super("INVALID_STATUS_TRANSITION", entity + " cannot move from " + from + " to " + to);
    }
}
