package com.amazon.amazonpaymerchanturl.task;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponse;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.WorkflowRequestItem;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.MANUAL_REVIEW;

@Log4j2
public class StartManualWorkflowTask extends PostUrlReviewActionBaseTask {

    public StartManualWorkflowTask(final ObjectMapper objectMapper, final StepFunctionAdapter stepFunctionAdapter,
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

        request.getWorkflowRequestItemList().forEach(workflowRequestItem ->
                processCurrentWorkflowRequestItem(postUrlReviewActionResponse, workflowRequestItem));

        return postUrlReviewActionResponse;
    }

    private void processCurrentWorkflowRequestItem(PostUrlReviewActionResponse postUrlReviewActionResponse,
                                                        final WorkflowRequestItem workflowRequestItem) {
        final String clientReferenceGroupId = workflowRequestItem.getClientReferenceGroupId();
        final String url = workflowRequestItem.getUrl();
        try {
            AmazonPayDomainValidationItem amazonPayDomainValidationItem
                    = getDomainValidationDDBEntry(clientReferenceGroupId, url);
            log.info("domain validation ddb entry for clientReferenceGroupId: {} and url: {}"
                    + " is: {}", clientReferenceGroupId, url, amazonPayDomainValidationItem);
            if (Objects.isNull(amazonPayDomainValidationItem)
                    || !isValidDomainValidationDDBEntry(amazonPayDomainValidationItem)) {
                log.info("Invalid domain validation ddb entry for clientReferenceGroupId: {}, and url: {}",
                        clientReferenceGroupId, url);
                postUrlReviewActionResponse.getFailedResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
                return;
            }

            String workflowId = amazonPayDomainValidationItem.getInvestigationId();
            final UrlInvestigationItem urlInvestigationItem
                    = getUrlInvestigationDDBEntry(workflowId, SubInvestigationType.MANUAL.getSubInvestigationType());
            log.info("url investigation ddb entry for workflowId: {} and subInvestigationType: {} is: {}",
                    workflowId, SubInvestigationType.MANUAL.getSubInvestigationType(), urlInvestigationItem);
            if (isValidUrlInvestigationDDBEntry(urlInvestigationItem)) {
                workflowId = startManualWorkflow(workflowId);
                updateDomainValidationWorkflowId(clientReferenceGroupId, url, workflowId);
                postUrlReviewActionResponse.getSuccessResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url, workflowId));
            } else {
                postUrlReviewActionResponse.getFailedResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
            }
        } catch (AmazonPayMerchantURLBaseException e) {
            log.info("Exception encountered while executing UrlReviewWorkflow for URL: {}", url, e);
            postUrlReviewActionResponse.getFailedResponseItemList()
                    .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
        }
    }

    private boolean isValidUrlInvestigationDDBEntry(final UrlInvestigationItem urlInvestigationItem) {
        if (Objects.isNull(urlInvestigationItem)
                || Objects.isNull(urlInvestigationItem.getSubInvestigationStatus())
                || !StringUtils.equals(SubInvestigationStatus.IN_REVIEW.getSubInvestigationStatus(),
                urlInvestigationItem.getSubInvestigationStatus())) {
            log.info("invalid urlInvestigation ddb entry for workflowId: {} and subInvestigationType: {}",
                    urlInvestigationItem.getInvestigationId(), SubInvestigationType.MANUAL.getSubInvestigationType());
            return false;
        }
        return true;
    }

    private String startManualWorkflow(final String workflowId) {
        final String stepFunctionArn = urlReviewWorkflowMap.get(MANUAL_REVIEW);
        final String input = stepFunctionAdapter.getWorkflowInput(workflowId);
        return stepFunctionAdapter.startWorkflow(stepFunctionArn, input);
    }
}
