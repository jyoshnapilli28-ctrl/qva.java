package com.privatechat.operations;

import org.springframework.http.HttpStatus;

public final class OperationsException extends RuntimeException {
    private final HttpStatus status;

    OperationsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
