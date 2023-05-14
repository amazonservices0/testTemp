package com.amazon.amazonpaymerchanturl.exceptions;

/**
 * The exception to be thrown when client has passed insufficient/invalid data.
 * It means that client is at fault and shouldn't retry.
 */
public class AmazonPayMerchantURLInvalidInputException extends AmazonPayMerchantURLBaseException {
    public AmazonPayMerchantURLInvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }

    public AmazonPayMerchantURLInvalidInputException(String message) {
        super(message);
    }
}
