package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Pojo to get InitiateManualReview Lambda input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiateManualReviewRequest {

    @JsonProperty("url")
    private String reviewURL;

    @JsonProperty("investigationType")
    private String investigationType;

    @JsonProperty("clientReferenceGroupId")
    private String clientReferenceGroupId;

    @JsonProperty("clientCustomInformation")
    private String clientCustomInformation;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("source")
    private String source;

    @JsonProperty("retryCount")
    @Builder.Default
    private Long retryCount = 0L;

    @JsonProperty("taskToken")
    private String taskToken;

    @JsonProperty("investigationId")
    private String investigationId;

    @JsonProperty("riskLevel")
    private String riskLevel;
}
