package com.amazon.amazonpaymerchanturl.task;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAOServiceBaseException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.constants.UpdateStatusType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.model.UrlReviewNotificationMessage;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponse;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponseItem;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazonaws.services.stepfunctions.model.ExecutionListItem;
import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import com.amazonaws.services.stepfunctions.model.ListExecutionsResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.AMAZON_PAY_BUSINESS;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.HYPHEN;

@Log4j2
public abstract class PostUrlReviewActionBaseTask {

    private static final String DATE_FORMAT = "dd/MM/yyyy";

    protected final ObjectMapper objectMapper;
    protected final StepFunctionAdapter stepFunctionAdapter;
    protected final DomainValidationDDBAdapter domainValidationDDBAdapter;
    protected final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    protected final SNSAdapter snsAdapter;
    protected final String urlStatusNotificationTopic;
    protected final Map<String, String> urlReviewWorkflowMap;

    protected PostUrlReviewActionBaseTask(final ObjectMapper objectMapper,
                                          final StepFunctionAdapter stepFunctionAdapter,
                                          final DomainValidationDDBAdapter domainValidationDDBAdapter,
                                          final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                          final SNSAdapter snsAdapter, final String urlStatusNotificationTopic,
                                          final Map<String, String> urlReviewWorkflowMap) {
        this.objectMapper = objectMapper;
        this.stepFunctionAdapter = stepFunctionAdapter;
        this.domainValidationDDBAdapter = domainValidationDDBAdapter;
        this.urlInvestigationDDBAdapter = urlInvestigationDDBAdapter;
        this.snsAdapter = snsAdapter;
        this.urlStatusNotificationTopic = urlStatusNotificationTopic;
        this.urlReviewWorkflowMap = urlReviewWorkflowMap;
    }

    public abstract PostUrlReviewActionResponse handleTask(@NonNull PostUrlReviewActionRequest request);

    public List<ExecutionListItem> getExecutionListItems(@NonNull final String startDateString,
                                                         @NonNull final String endDateString) {

        List<ExecutionListItem> executionListItemList = new ArrayList<>();
        try {
            final String stepFunctionArn = urlReviewWorkflowMap.get(AMAZON_PAY_BUSINESS);
            log.info("stepFunctionArn: {}", stepFunctionArn);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ROOT);
            final Date startDate = Date.from(LocalDate.parse(startDateString, formatter)
                    .atStartOfDay(ZoneOffset.UTC).toInstant());
            final Date endDate = Date.from(LocalDate.parse(endDateString, formatter)
                    .atStartOfDay(ZoneOffset.UTC).toInstant());
            String nextToken = null;
            boolean fetchMoreFailedWorkflows;
            do {
                fetchMoreFailedWorkflows = false;
                ListExecutionsResult listExecutionsResult = stepFunctionAdapter
                        .getExecutionsForGivenStatus(stepFunctionArn, ExecutionStatus.FAILED.toString(),
                                50, nextToken);
                log.info("listExecutionResult is: {}", listExecutionsResult);
                if (Objects.nonNull(listExecutionsResult)) {
                    executionListItemList.addAll(listExecutionsResult.getExecutions().stream()
                            .filter(executionListItem -> executionListItem.getStartDate().after(startDate)
                                    && executionListItem.getStartDate().before(endDate))
                            .collect(Collectors.toList()));

                    final int size = listExecutionsResult.getExecutions().size();
                    if (listExecutionsResult.getExecutions().get(size - 1).getStartDate().after(startDate)) {
                        fetchMoreFailedWorkflows = true;
                    }
                    nextToken = listExecutionsResult.getNextToken();
                }
            } while (fetchMoreFailedWorkflows);
        } catch (final AmazonPayMerchantURLBaseException e) {
            log.info("Exception encountered while getting the listExecution result using stepFunction adapter "
                    + "with status: {}", ExecutionStatus.FAILED.toString(), e);
        }

        return executionListItemList;
    }

    /**
     * This method returns the domain validation ddb entry.
     *
     * @param clientReferenceGroupId clientReferenceGroupId
     * @param url                    review url
     * @return AmazonPayDomainValidationItem
     */
    public AmazonPayDomainValidationItem getDomainValidationDDBEntry(
            @NonNull final String clientReferenceGroupId, @NonNull final String url) {
        try {
            return domainValidationDDBAdapter.loadEntry(clientReferenceGroupId, url);
        } catch (final AmazonPayDomainValidationDAONonRetryableException e) {
            final String msg = "Non retryable exception occur while fetching the domain validation ddb entry for "
                    + "clientReferenceGroupId: " + clientReferenceGroupId + " and url: " + url;
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (final AmazonPayDomainValidationDAORetryableException e) {
            final String msg = "Retryable exception occur while fetching the domain validation ddb entry for "
                    + "clientReferenceGroupId: " + clientReferenceGroupId + " and url: " + url;
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }
    }

    public boolean isValidDomainValidationDDBEntry(@NonNull final AmazonPayDomainValidationItem
                                                           amazonPayDomainValidationItem) {
        if (Objects.isNull(amazonPayDomainValidationItem.getInvestigationId()) ||
                Objects.isNull(amazonPayDomainValidationItem.getInvestigationStatus())) {
            return false;
        }
        return isWorkflowFailed(amazonPayDomainValidationItem.getInvestigationId());
    }

    private boolean isWorkflowFailed(final String workflowId) {
        String workflowStatus = stepFunctionAdapter.getWorkflowStatus(workflowId);
        log.info("Workflow Status for workflowId: {} is: {}", workflowId, workflowStatus);
        List<String> invalidStatusList = List.of(ExecutionStatus.FAILED.toString(),
                ExecutionStatus.ABORTED.toString(), ExecutionStatus.TIMED_OUT.toString());

        return invalidStatusList.contains(workflowStatus);
    }

    /**
     * This method create the urlInvestigation ddb entry and update the domainValidation ddb entry.
     *
     * @param clientReferenceGroupId clientReferenceGroupId
     * @param url                    review url
     * @param investigationStatus    investigation status
     * @param urlInvestigationItem   urlInvestigationItem
     */
    public void createDDBEntryAndPublishNotification(@NonNull final UrlInvestigationItem urlInvestigationItem,
                                                        @NonNull final String clientReferenceGroupId,
                                                        @NonNull final String url,
                                                        @NonNull final String investigationStatus,
                                                        @NonNull final Long reviewTime,
                                                     final Set<String> variantUrlList) {
        try {
            urlInvestigationDDBAdapter.createOrUpdateEntry(urlInvestigationItem);
            domainValidationDDBAdapter.createOrUpdateEntry(AmazonPayDomainValidationItem.builder()
                    .clientReferenceGroupId(clientReferenceGroupId)
                    .url(url)
                    .investigationStatus(investigationStatus)
                    .build());

            if (CollectionUtils.isEmpty(variantUrlList)) {
                buildAndPublishStatusNotification(clientReferenceGroupId, url, investigationStatus, reviewTime);
            } else {
                variantUrlList.forEach(variantUrl -> buildAndPublishStatusNotification(clientReferenceGroupId,
                        variantUrl, investigationStatus, reviewTime));
            }
        } catch (final AmazonPayDomainValidationDAOServiceBaseException e) {
            final String msg = "Exception occur while saving the record in the ddb for clientReferenceGroupId: "
                    + clientReferenceGroupId + " url: " + url;
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        }
    }

    /**
     * Builds and publishes the review url notification
     */
    private void buildAndPublishStatusNotification(final String clientReferenceGroupId, final String url,
                                                   final String reviewStatus, final Long urlReviewTime) {
        final UrlReviewNotificationMessage urlReviewStatusNotification = UrlReviewNotificationMessage.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .updateStatusType(UpdateStatusType.REVIEW_STATUS)
                .reviewStatus(reviewStatus)
                .urlReviewTime(urlReviewTime)
                .build();
        try {
            log.info("Publishing message to SNS with ClientReferenceGroupId: {}, URL: {}, Status: {}",
                    clientReferenceGroupId, url, reviewStatus);
            snsAdapter.publishMessage(objectMapper.writeValueAsString(urlReviewStatusNotification),
                    urlStatusNotificationTopic);
        } catch (final Exception e) {
            log.info("Error publishing message to sns topic {}", urlStatusNotificationTopic, e);
            throw new AmazonPayMerchantURLNonRetryableException("Error occur while publishing the notification", e);
        }
    }

    @SuppressWarnings("ParameterNumber")
    public UrlInvestigationItem getUrlInvestigationDDBItem(@NonNull final String clientReferenceGroupId,
                                                           @NonNull final String url,
                                                           @NonNull final String investigationStatus,
                                                           @NonNull final String investigationId,
                                                           @NonNull final long reviewStartTime,
                                                           @NonNull final long reviewEndTime) {
        return UrlInvestigationItem.builder()
                .investigationId(investigationId)
                .subInvestigationType(SubInvestigationType.OPERATIONAL.getSubInvestigationType())
                .subInvestigationStatus(investigationStatus)
                .reviewStartTime(reviewStartTime)
                .reviewEndTime(reviewEndTime)
                .reviewStartDate(Instant.ofEpochMilli(reviewStartTime).truncatedTo(ChronoUnit.DAYS).toEpochMilli())
                .clientReferenceGroupIdUrl(clientReferenceGroupId + HYPHEN + url)
                .build();
    }

    public PostUrlReviewActionResponseItem getPostUrlReviewActionResponseItem(
            @NonNull final String clientReferenceGroupId, @NonNull final String url,
            @NonNull final String investigationId) {
        return PostUrlReviewActionResponseItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .investigationId(investigationId)
                .build();
    }

    /**
     * This method returns the url investigation ddb entry.
     *
     * @param investigationId      investigation id
     * @param subInvestigationType sub investigation type
     * @return UrlInvestigationItem
     */
    public UrlInvestigationItem getUrlInvestigationDDBEntry(@NonNull final String investigationId,
                                                            @NonNull final String subInvestigationType) {
        try {
            return urlInvestigationDDBAdapter.loadEntry(investigationId, subInvestigationType);
        } catch (final AmazonPayDomainValidationDAONonRetryableException e) {
            final String msg = "Non retryable exception occur while fetching the urlInvestigation ddb entry for "
                    + "investigationId:" + investigationId + " and subInvestigationType: " + subInvestigationType;
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (final AmazonPayDomainValidationDAORetryableException e) {
            final String msg = "Retryable exception occur while fetching the urlInvestigation ddb entry for "
                    + "investigationId:" + investigationId + " and subInvestigationType: " + subInvestigationType;
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }
    }

    public void updateDomainValidationWorkflowId(@NonNull final String clientReferenceGroupId,
                                                 @NonNull final String url, @NonNull final String workflowId) {
        final AmazonPayDomainValidationItem amazonPayDomainValidationItem = AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .investigationId(workflowId)
                .build();
        domainValidationDDBAdapter.createOrUpdateEntry(amazonPayDomainValidationItem);
    }

    public PostUrlReviewActionResponseItem getPostUrlReviewActionResponseItem(
            @NonNull final String clientReferenceGroupId, @NonNull final String url) {
        return PostUrlReviewActionResponseItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .build();
    }
}
