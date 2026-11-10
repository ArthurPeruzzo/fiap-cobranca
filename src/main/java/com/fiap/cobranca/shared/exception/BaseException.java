package com.fiap.cobranca.shared.exception;

import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {

    private final int statusCode;

    protected BaseException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
