package com.amazon.amazonpaymerchanturl.model;

import com.amazon.urlvendorreviewmodel.model.ScanSpec;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * UrlVendorReviewResponseInput model for a given url.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class UrlVendorReviewResponseInput {

    private String clientReferenceGroupId;

    @JsonProperty("url")
    private String reviewURL;

    private String investigationType;

    private ScanSpec scanSpec;

    private String investigationId;

    private String clientCustomInformation;

    @JsonCreator
    public UrlVendorReviewResponseInput(@JsonProperty(value = "clientReferenceGroupId", required = true)
                                                    String clientReferenceGroupId,
                                        @JsonProperty(value = "url", required = true) String reviewURL,
                                        @JsonProperty(value = "investigationType", required = true)
                                                    String investigationType,
                                        @JsonProperty(value = "scanSpec", required = true) ScanSpec scanSpec,
                                        @JsonProperty(value = "investigationId") String investigationId,
                                        @JsonProperty(value = "clientCustomInformation")
                                                    String clientCustomInformation) {
        this.clientReferenceGroupId = clientReferenceGroupId;
        this.reviewURL = reviewURL;
        this.investigationType = investigationType;
        this.scanSpec = scanSpec;
        this.investigationId = investigationId;
        this.clientCustomInformation = clientCustomInformation;
    }
}
