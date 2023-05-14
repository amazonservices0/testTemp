package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pojo to get ProcessInvestigationRequest Lambda input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessInvestigationRequest {

    @JsonProperty("clientReferenceGroupId")
    private String clientReferenceGroupId;

    @JsonProperty("url")
    private String url;

    @JsonProperty("investigationStatus")
    private String investigationStatus;

    @JsonProperty("reviewInfo")
    private String reviewInfo;

    @JsonProperty("investigationId")
    private String investigationId;

    @JsonProperty("investigationType")
    private String investigationType;

    @JsonProperty("caseId")
    private Long caseId;

    @JsonProperty("retryCount")
    @Builder.Default
    private Long retryCount = 0L;
}
