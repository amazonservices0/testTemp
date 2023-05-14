package com.amazon.amazonpaymerchanturl.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * The SNS message which is sent as part of Taluva 1B.
 * See wiki for more details: https://w.amazon.com/bin/view/AmazonPayUrlStore
 */
@Getter
@SuperBuilder
@EqualsAndHashCode
public class UrlReviewNotificationMessage extends UrlNotificationMessage {
    private final String reviewStatus;
    private final String customClientInfo;
    private final String metadata;
    private final Long urlReviewTime;
    private final String source;
    private final String reviewInfo;
    private final String subInvestigation;
}
