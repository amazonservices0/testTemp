package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * URLReviewRequest model for a given URL for URLReview workflow.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("ParameterNumber")
@Data
@Builder
public class URLReviewRequest {

    private String clientReferenceGroupId;

    @JsonProperty("url")
    private String reviewURL;

    private String investigationType;

    private String clientCustomInformation;

    private String severity;

    private String source;

    private String investigationId;

    private String subInvestigationType;

    private String subInvestigationTaskToken;

    @JsonCreator
    public URLReviewRequest(@JsonProperty(value = "clientReferenceGroupId", required = true)
                                    String clientReferenceGroupId,
                            @JsonProperty(value = "url", required = true) String reviewURL,
                            @JsonProperty(value = "investigationType", required = true) String investigationType,
                            @JsonProperty("clientCustomInformation") String clientCustomInformation,
                            @JsonProperty("severity") String severity,
                            @JsonProperty("source") String source,
                            @JsonProperty("investigationId") String investigationId,
                            @JsonProperty("subInvestigationType") String subInvestigationType,
                            @JsonProperty("taskToken") String subInvestigationTaskToken) {
        this.clientReferenceGroupId = clientReferenceGroupId;
        this.reviewURL = reviewURL;
        this.investigationType = investigationType;
        this.clientCustomInformation = clientCustomInformation;
        this.severity = severity;
        this.source = source;
        this.investigationId = investigationId;
        this.subInvestigationType = subInvestigationType;
        this.subInvestigationTaskToken = subInvestigationTaskToken;
    }
}
