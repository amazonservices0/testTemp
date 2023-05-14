package com.amazon.amazonpaymerchanturl.exceptions;

/**
 * This class is specific to CloudWatchMetric exceptions.
 */
public class AmazonPayMerchantURLCloudwatchMetricException extends AmazonPayMerchantURLBaseException {

    public AmazonPayMerchantURLCloudwatchMetricException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AmazonPayMerchantURLCloudwatchMetricException(final String message) {
        super(message);
    }
}
