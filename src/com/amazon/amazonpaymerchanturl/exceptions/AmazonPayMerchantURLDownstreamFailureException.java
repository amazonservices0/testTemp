package com.amazon.amazonpaymerchanturl.exceptions;

/**
 * The exception to be thrown when something has gone wrong while processing downstream requests.
 */
public class AmazonPayMerchantURLDownstreamFailureException extends AmazonPayMerchantURLBaseException {

    public AmazonPayMerchantURLDownstreamFailureException(final String message) {
        super(message);
    }

    public AmazonPayMerchantURLDownstreamFailureException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
