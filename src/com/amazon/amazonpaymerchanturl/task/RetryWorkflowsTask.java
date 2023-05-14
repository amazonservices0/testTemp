package com.amazon.amazonpaymerchanturl.task;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponse;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.WorkflowRequestItem;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.AMAZON_PAY_BUSINESS;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.COLON_DELIMITER;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.RETRY_SUFFIX;

@Log4j2
public class RetryWorkflowsTask extends PostUrlReviewActionBaseTask {

    public RetryWorkflowsTask(final ObjectMapper objectMapper, final StepFunctionAdapter stepFunctionAdapter,
                              final DomainValidationDDBAdapter domainValidationDDBAdapter,
                              final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                              final SNSAdapter snsAdapter, final String urlStatusNotificationTopic,
                              final Map<String, String> urlReviewWorkflowMap) {
        super(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter, urlInvestigationDDBAdapter,
                snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    @Override
    public PostUrlReviewActionResponse handleTask(@NonNull PostUrlReviewActionRequest request) {
        if (Objects.isNull(request.getWorkflowRequestItemList())) {
            throw new AmazonPayMerchantURLNonRetryableException("Invalid request, request item is empty");
        }

        PostUrlReviewActionResponse postUrlReviewActionResponse = new PostUrlReviewActionResponse();

        request.getWorkflowRequestItemList().forEach(WorkflowRequestItem ->
                processCurrentRetryWorkflowRequestItem(postUrlReviewActionResponse, WorkflowRequestItem));

        return postUrlReviewActionResponse;
    }

    private void processCurrentRetryWorkflowRequestItem(PostUrlReviewActionResponse postUrlReviewActionResponse,
                                                        final WorkflowRequestItem workflowRequestItem) {
        final String clientReferenceGroupId = workflowRequestItem.getClientReferenceGroupId();
        final String url = workflowRequestItem.getUrl();
        try {
            AmazonPayDomainValidationItem amazonPayDomainValidationItem
                    = getDomainValidationDDBEntry(clientReferenceGroupId, url);
            log.info("domain validation ddb entry for clientReferenceGroupId: {} and url: {} is: {}",
                    clientReferenceGroupId, url, amazonPayDomainValidationItem);
            if (Objects.isNull(amazonPayDomainValidationItem)
                    || !isValidDomainValidationDDBEntry(amazonPayDomainValidationItem)) {
                log.info("Invalid domain validation ddb entry for clientReferenceGroupId: {}, and url: {}",
                        clientReferenceGroupId, url);
                postUrlReviewActionResponse.getFailedResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
                return;
            }

            String workflowId = amazonPayDomainValidationItem.getInvestigationId();
            workflowId = retryWorkflow(workflowId);
            updateDomainValidationWorkflowId(clientReferenceGroupId, url, workflowId);
            postUrlReviewActionResponse.getSuccessResponseItemList()
                    .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url, workflowId));
        } catch (AmazonPayMerchantURLBaseException e) {
            log.info("Exception encountered while executing UrlReviewWorkflow for URL: {}", url, e);
            postUrlReviewActionResponse.getFailedResponseItemList()
                    .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
        }
    }

    private String retryWorkflow(final String workflowId) {
        final String stepFunctionArn = urlReviewWorkflowMap.get(AMAZON_PAY_BUSINESS);
        // Split investigationId with colon. last part of investigationId is the workflowName.
        final String workflowName = workflowId.split(COLON_DELIMITER)
                [workflowId.split(COLON_DELIMITER).length - 1];
        final String input = stepFunctionAdapter.getWorkflowInput(workflowId);
        return stepFunctionAdapter.startWorkflow(stepFunctionArn, input, workflowName + RETRY_SUFFIX);
    }
}
