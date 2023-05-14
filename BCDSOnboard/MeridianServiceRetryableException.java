package com.amazon.meridianservice.exceptions;

public class MeridianServiceRetryableException extends MeridianServiceBaseException {
    public MeridianServiceRetryableException(final String message) {
        super(message);
    }

    public MeridianServiceRetryableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
