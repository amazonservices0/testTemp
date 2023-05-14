package com.amazon.amazonpaymerchanturl.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Paragon Investigation Response body.
 */
@Getter
@Builder
public class WebsiteReviewResponse {

    private Long investigationId;

    private String investigationStatus;

}
