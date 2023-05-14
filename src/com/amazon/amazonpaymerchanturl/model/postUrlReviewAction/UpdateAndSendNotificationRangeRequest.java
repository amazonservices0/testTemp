package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class UpdateAndSendNotificationRangeRequest {

    private String startDate;

    private String endDate;

    private String subInvestigationType;

    private String investigationStatus;

    @JsonCreator
    public UpdateAndSendNotificationRangeRequest(@JsonProperty(value = "startDate", required = true) String startDate,
                                                 @JsonProperty(value = "endDate", required = true) String endDate,
                                                 @JsonProperty(value = "subInvestigationType", required = true)
                                                       String subInvestigationType,
                                                 @JsonProperty(value = "investigationStatus", required = true)
                                                       String investigationStatus) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.subInvestigationType = subInvestigationType;
        this.investigationStatus = investigationStatus;
    }
}
