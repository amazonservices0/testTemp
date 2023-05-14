package com.amazon.amazonpaymerchanturl.exceptions;

public class AmazonPayMerchantURLRetryableException extends AmazonPayMerchantURLBaseException {
    public AmazonPayMerchantURLRetryableException(final String message) {
        super(message);
    }

    public AmazonPayMerchantURLRetryableException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AmazonPayMerchantURLRetryableException(final String message, final Integer statusCode) {
        super(message, statusCode);
    }

    public AmazonPayMerchantURLRetryableException(final String message, final Throwable cause,
                                                  final Integer statusCode) {
        super(message, cause, statusCode);
    }
}
