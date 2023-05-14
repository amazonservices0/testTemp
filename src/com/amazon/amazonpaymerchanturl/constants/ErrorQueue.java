package com.amazon.amazonpaymerchanturl.constants;

/**
 * Constants for Error queue.
 */
public final class ErrorQueue {

    private ErrorQueue() {
    }

    /**
     * Message Retry Threshold.
     * Poller frequency will be greater than 15 min so message will retried for min 2 day.
     */
    public static final long RETRY_UPPER_LIMIT = 192;

    /**
     * Minimum message retry threshold.
     * Poller frequency will be greater than 15 min.
     */
    public static final long RETRY_LOWER_LIMIT = 24;
}
