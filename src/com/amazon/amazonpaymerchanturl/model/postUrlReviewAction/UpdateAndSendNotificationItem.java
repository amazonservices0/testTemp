package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class UpdateAndSendNotificationItem {

    private String clientReferenceGroupId;

    private String url;

    private String subInvestigationStatus;

    private String reviewInfo;

    @JsonCreator
    public UpdateAndSendNotificationItem(@JsonProperty(value = "clientReferenceGroupId", required = true)
                                                 String clientReferenceGroupId,
                                         @JsonProperty(value = "url", required = true) String url,
                                         @JsonProperty(value = "subInvestigationStatus", required = true)
                                                 String subInvestigationStatus,
                                         @JsonProperty(value = "reviewInfo") String reviewInfo) {
        this.clientReferenceGroupId = clientReferenceGroupId;
        this.url = url;
        this.subInvestigationStatus = subInvestigationStatus;
        this.reviewInfo = reviewInfo;
    }
}
