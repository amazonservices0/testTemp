package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class WorkflowRequestItem {

    private String clientReferenceGroupId;

    private String url;

    @JsonCreator
    public WorkflowRequestItem(@JsonProperty(value = "clientReferenceGroupId", required = true)
                                            String clientReferenceGroupId,
                               @JsonProperty(value = "url", required = true) String url) {
        this.clientReferenceGroupId = clientReferenceGroupId;
        this.url = url;
    }
}
