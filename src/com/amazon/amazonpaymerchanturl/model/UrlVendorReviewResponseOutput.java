package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * UrlVendorReviewResponseOutput model for response of different SubInvestigation and risk level.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class UrlVendorReviewResponseOutput {
    @JsonProperty("status")
    private String subInvestigationStatus;

    @JsonProperty("subInvestigationType")
    private String subInvestigationType;

    @JsonProperty("riskLevel")
    private String riskLevel;

    @JsonProperty("reviewTime")
    private Long reviewTime;

    @JsonProperty("isManualReviewRequired")
    private boolean isManualReviewRequired;

    @JsonProperty("isPasswordProtected")
    private boolean isPasswordProtected;

    @JsonProperty("isOffline")
    private boolean isOffline;
}
