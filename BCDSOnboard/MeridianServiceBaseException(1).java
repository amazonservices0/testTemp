package com.amazon.meridianservice.exceptions;

import lombok.Getter;

public class MeridianServiceBaseException extends RuntimeException {
    @Getter
    private Integer statusCode;

    public MeridianServiceBaseException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MeridianServiceBaseException(final String message) {
        super(message);
    }

    public MeridianServiceBaseException(final String message, final Throwable cause, final Integer statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public MeridianServiceBaseException(final String message, final Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
