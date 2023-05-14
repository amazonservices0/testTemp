package com.amazon.meridianservice.exceptions;

public class MeridianServiceNonRetryableException extends MeridianServiceBaseException {
    public MeridianServiceNonRetryableException(final String message) {
        super(message);
    }

    public MeridianServiceNonRetryableException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MeridianServiceNonRetryableException(final String message, final Integer statusCode) {
        super(message, statusCode);
    }

    public MeridianServiceNonRetryableException(final String message, final Throwable cause,
                                                final Integer statusCode) {
        super(message, cause, statusCode);
    }
}
