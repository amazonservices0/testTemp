package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * URLReviewResponse model for response of different SubInvestigations for URLReview workflow.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@Builder
public class URLReviewResponse {
    @JsonProperty("status")
    private String subInvestigationStatus;

    @JsonProperty("reviewTime")
    private Long reviewTime;

    @JsonProperty("subInvestigationType")
    private String subInvestigationType;
}
