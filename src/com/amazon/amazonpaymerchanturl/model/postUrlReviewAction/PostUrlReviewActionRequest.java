package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class PostUrlReviewActionRequest {

    private String taskType;

    private List<UpdateAndSendNotificationItem> updateAndSendNotificationItemList;

    private UpdateAndSendNotificationRangeRequest updateAndSendNotificationRangeRequest;

    private GetAllFailedWorkflowRequest getAllFailedWorkflowRequest;

    private List<WorkflowRequestItem> workflowRequestItemList;

    @JsonCreator
    public PostUrlReviewActionRequest(@JsonProperty(value = "taskType", required = true) String taskType,
                                      @JsonProperty(value = "updateAndSendNotificationItems")
                                                  List<UpdateAndSendNotificationItem>
                                              updateAndSendNotificationItemList,
                                      @JsonProperty(value = "updateAndSendNotificationRangeRequest")
                                                  UpdateAndSendNotificationRangeRequest
                                                  updateAndSendNotificationRangeRequest,
                                      @JsonProperty(value = "getAllFailedWorkflowRequest")
                                                  GetAllFailedWorkflowRequest getAllFailedWorkflowRequest,
                                      @JsonProperty(value = "workflowRequestItems")
                                                  List<WorkflowRequestItem> workflowRequestItemList) {
        this.taskType = taskType;
        this.updateAndSendNotificationItemList = updateAndSendNotificationItemList;
        this.updateAndSendNotificationRangeRequest = updateAndSendNotificationRangeRequest;
        this.getAllFailedWorkflowRequest = getAllFailedWorkflowRequest;
        this.workflowRequestItemList = workflowRequestItemList;
    }
}
