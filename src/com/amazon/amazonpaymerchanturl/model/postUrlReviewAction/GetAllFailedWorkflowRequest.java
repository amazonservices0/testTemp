package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class GetAllFailedWorkflowRequest {

    private String startDate;

    private String endDate;

    @JsonCreator
    public GetAllFailedWorkflowRequest(@JsonProperty(value = "startDate", required = true) String startDate,
                                       @JsonProperty(value = "endDate", required = true) String endDate) {
        this.startDate = startDate;
        this.endDate = endDate;

    }
}
