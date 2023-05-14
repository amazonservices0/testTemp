package com.amazon.amazonpaymerchanturl.constants;

import java.util.List;

/**
 * Constants for Handlers.
 */
public final class HandlerConstants {

    private HandlerConstants() {
    }

    /**
     * Amazon Pay Business Name.
     */
    public static final String AMAZON_PAY_BUSINESS = "AmazonPay";

    /**
     * Amazon Pay Manual Investigation Business Name.
     */
    public static final String MANUAL_REVIEW = "ManualReview";

    /**
     * Hyphen symbol.
     */
    public static final String HYPHEN = "-";

    /**
     * Retry suffix.
     */
    public static final String RETRY_SUFFIX = "-retry";

    /**
     * Retry suffix.
     */
    public static final String COLON_DELIMITER = ":";

    /**
     * Path params for clientReferenceGroupId key
     */
    public static final String PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY = "clientReferenceGroupId";

    /**
     * Path params for url key
     */
    public static final String QUERY_STRING_PARAM_URL_KEY = "url";
    /**
     * Weblab T1 Treatment.
     */
    public static final String TREATMENT_T1 = "T1";

    /**
     * Weblab name AP_WEBSITE_REVIEW_US.
     */
    public static final String AP_WEBSITE_REVIEW_US_WEBLAB = "AP_WEBSITE_REVIEW_US_520929";

    /**
     * No/Low risk level list.
     */
    public static final List<String> LOW_RISKLEVEL_LIST = List.of("No-Risk", "Low");
}
