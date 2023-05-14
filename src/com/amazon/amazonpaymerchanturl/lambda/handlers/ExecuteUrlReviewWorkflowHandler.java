package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.constants.InvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.UpdateStatusType;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazon.amazonpaymerchanturl.model.TriggerURLReviewRequest;
import com.amazon.amazonpaymerchanturl.model.URLReviewRequest;
import com.amazon.amazonpaymerchanturl.model.UrlReviewNotificationMessage;
import com.amazon.amazonpaymerchanturl.utils.URLStandardizeUtil;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_URL_REVIEW_WORKFLOW_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_URL_REVIEW_WORKFLOW_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.URL_REVIEW_WORKFLOW_NOT_AVAILABLE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.URL_STATUS_NOTIFICATION_TOPIC_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.BAD_REQUEST_FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.utils.URLCaseSensitivityConvertorUtil.getLowerCaseConvertedURL;

/**
 * ExecuteUrlReviewWorkflowHandler executes StepFunction for Url review.
 */
@RequiredArgsConstructor
@Builder
@Log4j2
public class ExecuteUrlReviewWorkflowHandler implements RequestStreamHandler {

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper mapper;
    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final StepFunctionAdapter stepFunctionAdapter;
    private final Map<String, String> urlReviewWorkflowMap;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final SNSAdapter snsAdapter;
    private final String urlStatusNotificationTopic;
    private final SQSAdapter dlqAdapter;
    private final String executeUrlReviewWorkflowDlqUrl;
    private final WeblabHelper weblabHelper;

    private static final String AMAZON_PAY_BUSINESS = "AmazonPay";

    public ExecuteUrlReviewWorkflowHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.mapper = lambdaComponent.providesObjectMapper();
        this.domainValidationDDBAdapter = lambdaComponent.providesDomainValidationDDBAdapter();
        this.stepFunctionAdapter = lambdaComponent.providesStepFunctionAdapter();
        this.urlReviewWorkflowMap = lambdaComponent.providesUrlReviewWorkflowMap();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.snsAdapter = lambdaComponent.providesSNSAdapter();
        this.urlStatusNotificationTopic = lambdaComponent.providesUrlStatusNotificationTopic();
        this.dlqAdapter = lambdaComponent.providesSQSAdapter();
        this.executeUrlReviewWorkflowDlqUrl = lambdaComponent.providesExecuteUrlReviewWorkflowDlqUrl();
        this.weblabHelper = lambdaComponent.provideWeblabHelper();
    }

    /**
     * handleRequest entry point for ExecuteUrlReviewWorkflow Lambda to execute StepFunction for Url review.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    @Override
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        TriggerURLReviewRequest request;
        try {
            request = mapper.readValue(inputStream, TriggerURLReviewRequest.class);
        } catch (final Exception e) {
            log.info("Error parsing inputStream to triggerURLReviewRequest", e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(EXECUTE_URL_REVIEW_WORKFLOW_FAILURE_METRICS);
            return;
        }

        final TriggerURLReviewRequest triggerURLReviewRequest = request;
        final Map<String, List<String>> reviewUrlMetadata = triggerURLReviewRequest.getReviewURLsMetaData();
        if (MapUtils.isEmpty(reviewUrlMetadata)) {
            log.info("No reviewUrlMetadata present for ClientReferenceGroupId {}",
                    triggerURLReviewRequest.getClientReferenceGroupId());
            return;
        }

        // Currently hard-coding to AmazonPay business, change it to fetch from request when we have it there
        final String stepFunctionArn = urlReviewWorkflowMap.get(AMAZON_PAY_BUSINESS);
        if (StringUtils.isBlank(stepFunctionArn)) {
            log.info("UrlReview workflow not defined for {}", AMAZON_PAY_BUSINESS);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(URL_REVIEW_WORKFLOW_NOT_AVAILABLE_METRICS);
            return;
        }

        Map<String, List<String>> failedEntries = new HashMap<>();
        reviewUrlMetadata.forEach((key, urls) -> {
            if (CollectionUtils.isEmpty(urls)) {
                log.info("No URLs present for the urlType {}", key);
                return;
            }

            List<String> failedUrls = new ArrayList<>();
            urls.forEach(url -> {
                /*
                Note : MetricFilter syntax pattern should be updated in CDK package accordingly
                if there is a change in Log message
                */
                log.info("[EXECUTE_DOMAIN_URL_REVIEW] process UrlReviewRequest for url: {}, urlType: {}" +
                                " clientRefGrpId: {} and urlSource: {}",
                        url, key, triggerURLReviewRequest.getClientReferenceGroupId(),
                        triggerURLReviewRequest.getSource());
                int statusCode = processUrlReviewRequest(url, stepFunctionArn, triggerURLReviewRequest);
                if (statusCode == SUCCESS_STATUS_CODE) {
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                            EXECUTE_URL_REVIEW_WORKFLOW_SUCCESS_METRICS);
                } else {
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                            EXECUTE_URL_REVIEW_WORKFLOW_FAILURE_METRICS);
                    failedUrls.add(url);
                }
            });

            if (!CollectionUtils.isEmpty(failedUrls)) {
                failedEntries.put(key, failedUrls);
            }
        });

        if (!CollectionUtils.isEmpty(failedEntries.entrySet())) {
            log.info("Pushing failed entries to DLQ");
            request.setReviewURLsMetaData(failedEntries);
            sendMessageToDlq(request);
        }
    }

    private int processUrlReviewRequest(final String url, final String stepFunctionArn,
                                        final TriggerURLReviewRequest triggerURLReviewRequest) {
        URLReviewRequest urlReviewRequest;
        final long reviewTime = Instant.now().toEpochMilli();
        String lowercaseUrl;
        try {
            lowercaseUrl = getLowerCaseConvertedURL(url);
            urlReviewRequest = getUrlReviewRequest(triggerURLReviewRequest, lowercaseUrl);

            //TODO : Remove the below (if)block after deduping/cleaning the existing variant url entries in DB
            boolean isReviewProcessedWithExistingVariantUrl = processWithExistingValidVariantUrlStatus(urlReviewRequest,
                    lowercaseUrl, reviewTime);
            if (isReviewProcessedWithExistingVariantUrl) {
                return SUCCESS_STATUS_CODE;
            }

            final String standardizedUrl = URLStandardizeUtil.standardize(lowercaseUrl);
            if (weblabHelper.isWeblabDialedUpForDedupingVariantUrls(urlReviewRequest.getClientReferenceGroupId())) {
                urlReviewRequest.setReviewURL(standardizedUrl);

                boolean isReviewProcessedWithExistingStandardizedUrl = processWithExistingValidStandardizedUrlStatus(
                        urlReviewRequest, lowercaseUrl, reviewTime);
                if (isReviewProcessedWithExistingStandardizedUrl) {
                    return SUCCESS_STATUS_CODE;
                }
            } else {
                //Not processing the review with StandardizedURl. Creating a DDB entry with input variant url
                createOrUpdateEntryInDB(urlReviewRequest, standardizedUrl);
            }
        } catch (Exception e) {
            log.error("Error while saving entry in DB", e);
            return FAILED_STATUS_CODE;
        }

        String workflowId;
        try {
            // Start Url Review workflow
            workflowId = stepFunctionAdapter.startWorkflow(
                    stepFunctionArn, mapper.writeValueAsString(urlReviewRequest));
            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[STARTED_NEW_WORKFLOW] Started UrlReviewWorkflow: {} for ClientRefGrpId: {} and url: {}",
                    workflowId, urlReviewRequest.getClientReferenceGroupId(), urlReviewRequest.getReviewURL());
        } catch (Exception e) {
            // Update Domain DB to change status from InReview to Unknown
            log.info("Exception encountered while executing UrlReviewWorkflow for URL: {}",
                    urlReviewRequest.getReviewURL(), e);
            updateDomainValidationDBStatus(urlReviewRequest, InvestigationStatus.UNKNOWN.getInvestigationStatus());
            return FAILED_STATUS_CODE;
        }

        // Publish In_Review status to SNS with input url
        buildAndPublishStatusNotification(urlReviewRequest.getClientReferenceGroupId(),
                lowercaseUrl, InvestigationStatus.IN_REVIEW.getInvestigationStatus(), reviewTime, null);

        try {
            // Update workflowId in DB
            domainValidationDDBAdapter.createOrUpdateEntry(AmazonPayDomainValidationItem.builder()
                    .clientReferenceGroupId(urlReviewRequest.getClientReferenceGroupId())
                    .url(urlReviewRequest.getReviewURL())
                    .investigationId(workflowId)
                    .build());
            return SUCCESS_STATUS_CODE;
        } catch (Exception e) {
            log.info("Exception encountered while updating workflowId for URL: {}",
                    urlReviewRequest.getReviewURL(), e);
            return FAILED_STATUS_CODE;
        }
    }


    /**
     * process Url Review request with the existing url in DDB
     * Check for the existing url entry as it is and send the notification if the url is  present and status is valid
     * @return true if the url is present with valid status else return false
     */
    private boolean processWithExistingValidVariantUrlStatus(final URLReviewRequest urlReviewRequest,
                                                             final String lowercaseUrl,
                                                             final long reviewTime) {
        AmazonPayDomainValidationItem domainValidationItem = domainValidationDDBAdapter.loadEntry(
                urlReviewRequest.getClientReferenceGroupId(), lowercaseUrl);
        if (isUrlActiveWithValidInvestigationStatus(domainValidationItem)) {
            log.info("ClientReferenceGroupId {}, Variant URL {} already active and " +
                            "investigation status is valid",
                    urlReviewRequest.getClientReferenceGroupId(), lowercaseUrl);
            // Send SNS notification if Compliant/NonCompliant
            getStatusAndPublishURLReviewNotification(urlReviewRequest.getClientReferenceGroupId(),
                    lowercaseUrl, reviewTime, lowercaseUrl);
            return true;
        }
        return false;
    }

    /**
     * process Url Review request with the existing url in DDB
     * Check for the existing standardized url entry and send notification if status is valid
     * or else create a new entry
     * Add the input variant url to the VariantURL List in DDB
     * @return true if the standardized url is present with valid status
     * else return false if urlReview Workflow needs to be started
     */
    private boolean processWithExistingValidStandardizedUrlStatus(final URLReviewRequest urlReviewRequest,
                                                                  final String lowercaseUrl,
                                                                  final long reviewTime) {
        boolean entryCreated = createEntryIfNotExistsWithStandardizedUrlInDB(urlReviewRequest, lowercaseUrl);
        if (!entryCreated) {
            log.info("ClientReferenceGroupId {}, Standard URL {} already present",
                    urlReviewRequest.getClientReferenceGroupId(), urlReviewRequest.getReviewURL());
            updateDDBVariantURLSetWithNewUrl(urlReviewRequest, lowercaseUrl);
            boolean updateEntry = updateEntryIfUrlNotActiveOrStatusUnknown(
                    urlReviewRequest.getClientReferenceGroupId(), urlReviewRequest.getReviewURL());
            if (!updateEntry) {
                // Standard Url is already active and investigationStatus is valid.
                log.info("ClientReferenceGroupId {}, Standardize URL {} already active and " +
                                "investigation status is valid",
                        urlReviewRequest.getClientReferenceGroupId(), urlReviewRequest.getReviewURL());
                // Send SNS notification if Compliant/NonCompliant
                getStatusAndPublishURLReviewNotification(urlReviewRequest.getClientReferenceGroupId(),
                        urlReviewRequest.getReviewURL(), reviewTime, lowercaseUrl);
                return true;
            }
        }
        return false;
    }

    private boolean updateEntryIfUrlNotActiveOrStatusUnknown(final String clientReferenceGroupId, final String url) {
        AmazonPayDomainValidationItem updatedDDBEntry = AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .investigationStatus(InvestigationStatus.IN_REVIEW.getInvestigationStatus())
                .isActive(Boolean.TRUE)
                .build();
        return domainValidationDDBAdapter.updateEntryIfUrlNotActiveOrStatusUnknown(updatedDDBEntry);
    }

    /*
     * UpdateDDB entry by appending the new url to the variantURL list of standardized url in DDB
     */
    private void updateDDBVariantURLSetWithNewUrl(final URLReviewRequest urlReviewRequest,
                                                  final String lowercaseUrl) {
        AmazonPayDomainValidationItem ddbItem = AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(urlReviewRequest.getClientReferenceGroupId())
                .url(urlReviewRequest.getReviewURL())
                .variantURLs(Collections.singleton(lowercaseUrl))
                .build();
        domainValidationDDBAdapter.createOrUpdateEntryWithVariantUrlSetAppend(ddbItem);
    }

    /**
     * checks if url status is valid and ctive
     * returns true , if url status is not UNKNOWN and isActive=true, else false
     */
    private  boolean isUrlActiveWithValidInvestigationStatus(final AmazonPayDomainValidationItem domainValidationItem) {
        return Objects.nonNull(domainValidationItem) &&
                !StringUtils.equals(domainValidationItem.getInvestigationStatus(),
                        InvestigationStatus.UNKNOWN.getInvestigationStatus()) &&
                !Boolean.FALSE.equals(domainValidationItem.getIsActive());
    }

    private URLReviewRequest getUrlReviewRequest(final TriggerURLReviewRequest triggerURLReviewRequest,
                                                 final String url) {
        return URLReviewRequest.builder()
                .clientReferenceGroupId(triggerURLReviewRequest.getClientReferenceGroupId())
                .reviewURL(url)
                .investigationType(triggerURLReviewRequest.getInvestigationType())
                .clientCustomInformation(triggerURLReviewRequest.getClientCustomInformation())
                .source(triggerURLReviewRequest.getSource())
                .severity(triggerURLReviewRequest.getSeverity())
                .build();
    }

    private boolean createEntryIfNotExistsWithStandardizedUrlInDB(final URLReviewRequest urlReviewRequest,
                                                                  final String lowercaseUrl) {
        final AmazonPayDomainValidationItem ddbEntry = AmazonPayDomainValidationItem.builder()
                .url(urlReviewRequest.getReviewURL())
                .clientReferenceGroupId(urlReviewRequest.getClientReferenceGroupId())
                .clientInfo(urlReviewRequest.getClientCustomInformation())
                .urlSource(urlReviewRequest.getSource())
                .caseCreationTime(Instant.now().toEpochMilli())
                .investigationStatus(InvestigationStatus.IN_REVIEW.getInvestigationStatus())
                .isActive(Boolean.TRUE)
                .variantURLs(Collections.singleton(lowercaseUrl))
                .normalizedUrl(urlReviewRequest.getReviewURL())
                .build();
        return domainValidationDDBAdapter.createEntryIfNotExists(ddbEntry);
    }

    private void createOrUpdateEntryInDB(final URLReviewRequest urlReviewRequest, final String standardizedUrl) {
        final AmazonPayDomainValidationItem ddbEntry = AmazonPayDomainValidationItem.builder()
                .url(urlReviewRequest.getReviewURL())
                .clientReferenceGroupId(urlReviewRequest.getClientReferenceGroupId())
                .clientInfo(urlReviewRequest.getClientCustomInformation())
                .urlSource(urlReviewRequest.getSource())
                .caseCreationTime(Instant.now().toEpochMilli())
                .investigationStatus(InvestigationStatus.IN_REVIEW.getInvestigationStatus())
                .normalizedUrl(standardizedUrl)
                .isActive(Boolean.TRUE)
                .build();
        domainValidationDDBAdapter.createOrUpdateEntry(ddbEntry);
    }

    /**
     * Gets status of URL to publish SNS notification
     */
    private void getStatusAndPublishURLReviewNotification(final String clientReferenceGroupId, final String url,
                                                          final long reviewTime, final String inputUrl) {
        AmazonPayDomainValidationItem domainValidationItem;
        try {
            domainValidationItem = domainValidationDDBAdapter.loadEntry(clientReferenceGroupId, url);
        } catch (final Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(URL_STATUS_NOTIFICATION_TOPIC_FAILED);
            log.info("Error getting status for publishing message to SNS", e);
            return;
        }

        // If the URL is already Compliant/NonCompliant, publish SNS notification
        final String status = domainValidationItem.getInvestigationStatus();
        if (StringUtils.equals(InvestigationStatus.COMPLIANT.getInvestigationStatus(), status)
                || StringUtils.equals(InvestigationStatus.NON_COMPLIANT.getInvestigationStatus(), status)) {
            buildAndPublishStatusNotification(clientReferenceGroupId, inputUrl, status, reviewTime,
                    domainValidationItem.getSubInvestigationType());
        }
    }

    /**
     * Builds and publishes the review url notification
     */
    private void buildAndPublishStatusNotification(final String clientReferenceGroupId, final String url,
                                                   final String reviewStatus, final long reviewTime,
                                                   final String subInvestigation) {
        final UrlReviewNotificationMessage urlReviewStatusNotification = UrlReviewNotificationMessage.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .updateStatusType(UpdateStatusType.REVIEW_STATUS)
                .reviewStatus(reviewStatus)
                .subInvestigation(subInvestigation)
                .urlReviewTime(reviewTime)
                .build();
        try {
            log.info("Publishing message to SNS with ClientReferenceGroupId: {}, URL: {}, Status: {}",
                    clientReferenceGroupId, url, reviewStatus);
            snsAdapter.publishMessage(mapper.writeValueAsString(urlReviewStatusNotification),
                    urlStatusNotificationTopic);
        } catch (final Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(URL_STATUS_NOTIFICATION_TOPIC_FAILED);
            log.info("Error publishing message to sns topic {}", urlStatusNotificationTopic, e);
        }
    }

    private int updateDomainValidationDBStatus(final URLReviewRequest urlReviewRequest, final String status) {
        final AmazonPayDomainValidationItem ddbItem = AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(urlReviewRequest.getClientReferenceGroupId())
                .url(urlReviewRequest.getReviewURL())
                .investigationStatus(status)
                .build();
        try {
            domainValidationDDBAdapter.createOrUpdateEntry(ddbItem);
            return SUCCESS_STATUS_CODE;
        } catch (final AmazonPayDomainValidationDAORetryableException ex) {
            log.info("Retryable Exception while updating status to {} for URL: {}", status,
                    urlReviewRequest.getReviewURL(), ex);
            return FAILED_STATUS_CODE;
        } catch (final AmazonPayDomainValidationDAONonRetryableException ex) {
            log.info("NonRetryable Exception while updating status to {} for URL: {}", status,
                    urlReviewRequest.getReviewURL(), ex);
            return BAD_REQUEST_FAILED_STATUS_CODE;
        }
    }

    /**
     * Pushing the failed messages to dlq.
     */
    private void sendMessageToDlq(final TriggerURLReviewRequest triggerURLReviewRequest) {
        try {
            dlqAdapter.sendMessage(mapper.writeValueAsString(triggerURLReviewRequest), executeUrlReviewWorkflowDlqUrl);
        } catch (JsonProcessingException e) {
            log.info("Failed to send message triggerUrlReviewRequest {} to dlq.", triggerURLReviewRequest, e);
        }
    }
}
