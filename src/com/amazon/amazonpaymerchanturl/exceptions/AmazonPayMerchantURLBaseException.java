package com.amazon.amazonpaymerchanturl.exceptions;

import lombok.Getter;

public class AmazonPayMerchantURLBaseException extends RuntimeException {

    @Getter
    private Integer statusCode;

    public AmazonPayMerchantURLBaseException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AmazonPayMerchantURLBaseException(final String message) {
        super(message);
    }

    public AmazonPayMerchantURLBaseException(final String message, final Throwable cause, final Integer statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public AmazonPayMerchantURLBaseException(final String message, final Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
