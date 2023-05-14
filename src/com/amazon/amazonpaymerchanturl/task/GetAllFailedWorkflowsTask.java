package com.amazon.amazonpaymerchanturl.task;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAOServiceBaseException;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.GetAllFailedWorkflowRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponse;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponseItem;
import com.amazon.amazonpaymerchanturl.utils.HandlersUtil;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazonaws.services.stepfunctions.model.ExecutionListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Log4j2
public class GetAllFailedWorkflowsTask extends PostUrlReviewActionBaseTask {

    public GetAllFailedWorkflowsTask(final ObjectMapper objectMapper, final StepFunctionAdapter stepFunctionAdapter,
                                     final DomainValidationDDBAdapter domainValidationDDBAdapter,
                                     final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                     final SNSAdapter snsAdapter, final String urlStatusNotificationTopic,
                                     final Map<String, String> urlReviewWorkflowMap) {
        super(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter, urlInvestigationDDBAdapter,
                snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    /**
     * This method will get all the workflow which has failed in given date range and return that.
     *
     * @param request getAllFailedWorkflowRequest
     * @return PostUrlReviewActionResponse
     */
    @Override
    public PostUrlReviewActionResponse handleTask(@NonNull PostUrlReviewActionRequest request) {
        if (Objects.isNull(request.getGetAllFailedWorkflowRequest())) {
            throw new AmazonPayMerchantURLNonRetryableException(
                    "Invalid request, Get all failed workflow request is null");
        }

        PostUrlReviewActionResponse postUrlReviewActionResponse = new PostUrlReviewActionResponse();
        final GetAllFailedWorkflowRequest getAllFailedWorkflowRequest = request.getGetAllFailedWorkflowRequest();
        final List<ExecutionListItem> executionListItems = getExecutionListItems(getAllFailedWorkflowRequest
                .getStartDate(), getAllFailedWorkflowRequest.getEndDate());
        executionListItems.forEach(executionListItem -> {
            try {
                List<UrlInvestigationItem> urlInvestigationItemList = urlInvestigationDDBAdapter
                        .queryByInvestigationId(executionListItem.getExecutionArn());
                if (CollectionUtils.isEmpty(urlInvestigationItemList)
                        || Objects.isNull(urlInvestigationItemList.get(0).getClientReferenceGroupIdUrl())) {
                    log.info("Invalid ddb entries: {}", urlInvestigationItemList);
                    postUrlReviewActionResponse.getFailedResponseItemList()
                            .add(PostUrlReviewActionResponseItem.builder()
                                    .investigationId(executionListItem.getExecutionArn()).build());
                    return;
                }

                final String clientReferenceGroupId = HandlersUtil
                        .getClientReferenceGroupId(urlInvestigationItemList.get(0).getClientReferenceGroupIdUrl());
                final String url = HandlersUtil
                        .getUrl(urlInvestigationItemList.get(0).getClientReferenceGroupIdUrl());
                postUrlReviewActionResponse.getSuccessResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url,
                                executionListItem.getExecutionArn()));
            } catch (AmazonPayDomainValidationDAOServiceBaseException e) {
                log.info("Exception occur while querying the ddb for investigationId: {}",
                        executionListItem.getExecutionArn(), e);
                postUrlReviewActionResponse.getFailedResponseItemList().add(PostUrlReviewActionResponseItem.builder()
                        .investigationId(executionListItem.getExecutionArn()).build());
            }
        });
        return postUrlReviewActionResponse;
    }
}
