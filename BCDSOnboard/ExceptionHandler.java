package com.amazon.meridianservice.utils;

import com.amazon.meridianservice.exceptions.MeridianServiceBaseException;
import com.amazon.meridianservice.exceptions.MeridianServiceNonRetryableException;
import com.amazon.meridianservice.exceptions.MeridianServiceRetryableException;
import com.amazonaws.AmazonServiceException;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class ExceptionHandler {
    private ExceptionHandler() {
    }

    public static MeridianServiceBaseException handleAwsSdkServiceException(@NonNull final String message,
                                                                            @NonNull final AmazonServiceException e) {
        switch (e.getErrorType()) {
            case Unknown:
            case Service:
                return new MeridianServiceRetryableException(message, e);
            case Client:
            default: return new MeridianServiceNonRetryableException(message, e);
        }
    }
}
