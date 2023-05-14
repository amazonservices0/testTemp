package com.amazon.amazonpaymerchanturl.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Delete Merchant Response body.
 */
@Getter
@Builder
public class DeleteMerchantResponse {

    final String clientReferenceGroupId;

    final List<String> successUrls;

    final List<String> failedUrls;
}
