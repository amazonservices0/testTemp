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
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Log4j2
public class UpdateAndSendNotificationTask extends PostUrlReviewActionBaseTask {

    public UpdateAndSendNotificationTask(final ObjectMapper objectMapper,
                                         final StepFunctionAdapter stepFunctionAdapter,
                                         final DomainValidationDDBAdapter domainValidationDDBAdapter,
                                         final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                         final SNSAdapter snsAdapter, final String urlStatusNotificationTopic,
                                         final Map<String, String> urlReviewWorkflowMap) {
        super(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter, urlInvestigationDDBAdapter,
                snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    /**
     * This method will create the urlInvestigation ddb entry and update the domainValidation ddb entry.
     * And publish the sns notification.
     *
     * @param request list of the item for which ddb entry has to update.
     * @return postUrlReviewActionResponse
     */
    @Override
    public PostUrlReviewActionResponse handleTask(@NonNull PostUrlReviewActionRequest request) {
        if (Objects.isNull(request.getUpdateAndSendNotificationItemList())) {
            throw new AmazonPayMerchantURLNonRetryableException(
                    "Invalid request, update and send notification request item is empty");
        }

        PostUrlReviewActionResponse postUrlReviewActionResponse = new PostUrlReviewActionResponse();
        request.getUpdateAndSendNotificationItemList().forEach(updateAndSendNotificationItem -> {
            log.info("Current postUrlReviewActionDBItem is: {}", updateAndSendNotificationItem);
            final String clientReferenceGroupId = updateAndSendNotificationItem.getClientReferenceGroupId();
            final String url = updateAndSendNotificationItem.getUrl();
            final String investigationStatus = updateAndSendNotificationItem.getSubInvestigationStatus();
            final Long reviewTime = Instant.now().toEpochMilli();
            try {
                processCurrentRequestItem(clientReferenceGroupId, url, investigationStatus,
                        updateAndSendNotificationItem.getReviewInfo(), reviewTime, reviewTime);
                postUrlReviewActionResponse.getSuccessResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
            } catch (AmazonPayMerchantURLBaseException e) {
                log.info("Exception occur while processing the updateAndSendNotificationItem: {}",
                        updateAndSendNotificationItem, e);
                postUrlReviewActionResponse.getFailedResponseItemList()
                        .add(getPostUrlReviewActionResponseItem(clientReferenceGroupId, url));
            }
        });

        return postUrlReviewActionResponse;
    }

    public void processCurrentRequestItem(final String clientReferenceGroupId, final String url,
                                             final String investigationStatus, final String reviewInfo,
                                             final Long reviewStartTime, final Long reviewEndTime) {
        if (StringUtils.equals(InvestigationStatus.UNKNOWN.getInvestigationStatus(),
                InvestigationStatus.fromValue(investigationStatus).getInvestigationStatus())) {
            throw new AmazonPayMerchantURLNonRetryableException(
                    "Invalid Investigation status: " + investigationStatus);
        }

        final AmazonPayDomainValidationItem amazonPayDomainValidationItem
                = getDomainValidationDDBEntry(clientReferenceGroupId, url);
        log.info("Domain validation ddb entry for clientReferenceGroupId: {} and url: {} is: {}",
                clientReferenceGroupId, url, amazonPayDomainValidationItem);

        if (Objects.nonNull(amazonPayDomainValidationItem)
                && isValidDomainValidationDDBEntry(amazonPayDomainValidationItem)) {
            final String investigationId = amazonPayDomainValidationItem.getInvestigationId();
            final UrlInvestigationItem urlInvestigationItem = getUrlInvestigationDDBItem(clientReferenceGroupId,
                    url, investigationStatus, investigationId, reviewStartTime, reviewEndTime);
            urlInvestigationItem.setReviewInfo(reviewInfo);
            createDDBEntryAndPublishNotification(urlInvestigationItem, clientReferenceGroupId, url,
                    investigationStatus, reviewEndTime, amazonPayDomainValidationItem.getVariantURLs());
            return;
        }

        final String msg = "Invalid Current Request item for clientReferenceGroupId: " + clientReferenceGroupId
                + " and url: " + url;
        throw new AmazonPayMerchantURLNonRetryableException(msg);
    }
}
