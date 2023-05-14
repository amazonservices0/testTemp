package com.amazon.amazonpaymerchanturl.exceptions;


public class AmazonPayMerchantURLNonRetryableException extends AmazonPayMerchantURLBaseException {

    public AmazonPayMerchantURLNonRetryableException(final String message) {
        super(message);
    }

    public AmazonPayMerchantURLNonRetryableException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AmazonPayMerchantURLNonRetryableException(final String message, final Integer statusCode) {
        super(message, statusCode);
    }

    public AmazonPayMerchantURLNonRetryableException(final String message, final Throwable cause,
                                                     final Integer statusCode) {
        super(message, cause, statusCode);
    }
}
