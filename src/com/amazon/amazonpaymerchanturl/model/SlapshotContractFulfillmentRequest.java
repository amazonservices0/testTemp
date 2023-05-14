package com.amazon.amazonpaymerchanturl.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;

/**
 * Request entity to get slapshot fulfillment response.
 */
@Value
@Builder
public class SlapshotContractFulfillmentRequest {
    /**
     * Slapshot contract name.
     */
    @NonNull
    private String contractId;

    /**
     * Slapshot contract response node name.
     */
    @NonNull
    private String responseNode;

    /**
     * Contract project name.
     */
    @NonNull
    private String projectName;

    /**
     * Request fulfillment arguments.
     */
    @NonNull
    private Map<String, String> requestParams;
}
