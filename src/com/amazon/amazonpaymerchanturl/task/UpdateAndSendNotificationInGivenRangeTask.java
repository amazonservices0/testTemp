package com.amazon.amazonpaymerchanturl.task;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.constants.InvestigationStatus;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponse;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponseItem;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.UpdateAndSendNotificationRangeRequest;
import com.amazon.amazonpaymerchanturl.utils.HandlersUtil;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazonaws.services.stepfunctions.model.ExecutionListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.HYPHEN;

@Log4j2
public class UpdateAndSendNotificationInGivenRangeTask extends PostUrlReviewActionBaseTask {

    public UpdateAndSendNotificationInGivenRangeTask(final ObjectMapper objectMapper,
                                                     final StepFunctionAdapter stepFunctionAdapter,
                                                     final DomainValidationDDBAdapter domainValidationDDBAdapter,
                                                     final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                                     final SNSAdapter snsAdapter,
                                                     final String urlStatusNotificationTopic,
                                                     final Map<String, String> urlReviewWorkflowMap) {
        super(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter, urlInvestigationDDBAdapter,
                snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    /**
     * This method will create the urlInvestigation ddb entry and update the domainValidation ddb entry.
     * And publish the sns notification of all the workflows which has failed in given date range.
     *
     * @param request postUrlReviewActionCommonDBItem.
     * @return postUrlReviewActionResponse.
     */
    @Override
    public PostUrlReviewActionResponse handleTask(@NonNull PostUrlReviewActionRequest request) {
        if (Objects.isNull(request.getUpdateAndSendNotificationRangeRequest())
                || StringUtils.equals(InvestigationStatus.UNKNOWN.getInvestigationStatus(),
                InvestigationStatus.fromValue(request.getUpdateAndSendNotificationRangeRequest()
                        .getInvestigationStatus()).getInvestigationStatus())) {
            throw new AmazonPayMerchantURLNonRetryableException(
                    "Invalid request, update and send notification range request is null");
        }

        PostUrlReviewActionResponse postUrlReviewActionResponse = new PostUrlReviewActionResponse();
        final UpdateAndSendNotificationRangeRequest updateAndSendNotificationRangeRequest
                = request.getUpdateAndSendNotificationRangeRequest();
        final List<ExecutionListItem> executionListItemList
                = getExecutionListItems(updateAndSendNotificationRangeRequest.getStartDate(),
                updateAndSendNotificationRangeRequest.getEndDate());

        final String investigationStatus = updateAndSendNotificationRangeRequest.getInvestigationStatus();
        final String subInvestigationType = updateAndSendNotificationRangeRequest.getSubInvestigationType();
        executionListItemList.forEach(executionListItem -> processCurrentExecutionListItem(postUrlReviewActionResponse,
                investigationStatus, subInvestigationType, executionListItem));

        return postUrlReviewActionResponse;
    }

    private void processCurrentExecutionListItem(PostUrlReviewActionResponse postUrlReviewActionResponse,
                                                 final String investigationStatus, final String subInvestigationType,
                                                 final ExecutionListItem executionListItem) {
        log.info("Current executionListItem is: {}", executionListItem);
        final String investigationId = executionListItem.getExecutionArn();
        try {
            final UrlInvestigationItem urlInvestigationItem
                    = getUrlInvestigationDDBEntry(investigationId, subInvestigationType);
            log.info("existing url investigation ddb entry for investigationId: {} and subInvestigationType: {}"
                    + " is: {}", investigationId, subInvestigationType, urlInvestigationItem);

            if (Objects.isNull(urlInvestigationItem)
                    || !isValidUrlInvestigationDDBEntry(urlInvestigationItem)) {
                log.info("invalid urlInvestigation ddb entry for investigationId: {} and "
                        + "subInvestigationType: {}", investigationId, subInvestigationType);
                return;
            }
            final String clientReferenceGroupId = HandlersUtil
                    .getClientReferenceGroupId(urlInvestigationItem.getClientReferenceGroupIdUrl());
            final String url = HandlersUtil.getUrl(urlInvestigationItem.getClientReferenceGroupIdUrl());
            processCurrentRequestItem(clientReferenceGroupId, url, investigationStatus,
                    urlInvestigationItem.getSubInvestigationId(), urlInvestigationItem.getReviewStartTime(),
                    Instant.now().toEpochMilli());
            postUrlReviewActionResponse.getSuccessResponseItemList().add(getPostUrlReviewActionResponseItem(
                        clientReferenceGroupId, url, investigationId));
            System.out.println(postUrlReviewActionResponse);
        } catch (AmazonPayMerchantURLBaseException e) {
            log.error("Exception occur while processing the current executionListItem: {}", executionListItem, e);
            postUrlReviewActionResponse.getFailedResponseItemList().add(PostUrlReviewActionResponseItem.builder()
                    .investigationId(executionListItem.getExecutionArn()).build());
        }
    }

    public void processCurrentRequestItem(final String clientReferenceGroupId, final String url,
                                             final String investigationStatus, final String subInvestigationId,
                                             final Long reviewStartTime, final Long reviewEndTime) {
        final AmazonPayDomainValidationItem amazonPayDomainValidationItem
                = getDomainValidationDDBEntry(clientReferenceGroupId, url);
        log.info("Domain validation ddb entry for clientReferenceGroupId: {} and url: {} is: {}",
                clientReferenceGroupId, url, amazonPayDomainValidationItem);

        if (Objects.nonNull(amazonPayDomainValidationItem)
                && isValidDomainValidationDDBEntry(amazonPayDomainValidationItem)) {
            final String investigationId = amazonPayDomainValidationItem.getInvestigationId();
            final UrlInvestigationItem urlInvestigationItem = getUrlInvestigationDDBItem(clientReferenceGroupId,
                    url, investigationStatus, investigationId, reviewStartTime, reviewEndTime);
            urlInvestigationItem.setSubInvestigationId(subInvestigationId);
            createDDBEntryAndPublishNotification(urlInvestigationItem, clientReferenceGroupId, url,
                    investigationStatus, reviewEndTime, amazonPayDomainValidationItem.getVariantURLs());
            return;
        }

        final String msg = "Invalid Current execution list item for clientReferenceGroupId: " + clientReferenceGroupId
                + ", url: " + url;
        throw new AmazonPayMerchantURLNonRetryableException(msg);
    }

    private boolean isValidUrlInvestigationDDBEntry(final UrlInvestigationItem urlInvestigationItem) {
        final String clientReferenceGroupIdUrl = urlInvestigationItem.getClientReferenceGroupIdUrl();

        if (Objects.isNull(urlInvestigationItem.getSubInvestigationId())
                || Objects.isNull(urlInvestigationItem.getReviewStartTime())
                || Objects.isNull(clientReferenceGroupIdUrl)) {
            return false;
        }

        return clientReferenceGroupIdUrl.split(HYPHEN).length == 2;
    }
}
