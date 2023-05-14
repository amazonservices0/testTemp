package com.amazon.amazonpaymerchanturl.model;

import java.util.List;

import com.amazon.urlvendorreviewmodel.model.ScanSpec;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * UrlVendorReviewScanSpecInput model for scanspec input.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class UrlVendorReviewScanSpecInput {

    private List<ScanSpec> scanSpecList;

    @Builder.Default
    private Long retryCount = 0L;

    @JsonCreator
    public UrlVendorReviewScanSpecInput(@JsonProperty(value = "scanSpecList", required = true)
                                                    List<ScanSpec> scanSpecList,
                                        @JsonProperty(value = "retryCount", required = true) Long retryCount) {
        this.scanSpecList = scanSpecList;
        this.retryCount = retryCount;
    }
}
