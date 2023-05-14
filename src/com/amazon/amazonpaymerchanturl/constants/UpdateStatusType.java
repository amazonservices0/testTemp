package com.amazon.amazonpaymerchanturl.constants;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * Contains values for a update type.
 */
public enum UpdateStatusType {

    @JsonProperty("ReviewStatus")
    REVIEW_STATUS("ReviewStatus"),

    @JsonProperty("ActiveStatus")
    ACTIVE_STATUS("ActiveStatus");

    private final String value;
}
