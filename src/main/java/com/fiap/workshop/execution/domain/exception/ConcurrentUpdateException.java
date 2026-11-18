package com.fiap.workshop.execution.domain.exception;

/** Another writer changed the same execution order first; the caller should retry. */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
