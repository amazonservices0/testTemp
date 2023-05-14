package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Pojo to get TriggerURLReview Lambda input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerURLReviewRequest {
    @JsonProperty("reviewURLs")
    private Map<String, List<String>> reviewURLsMetaData;

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
}
