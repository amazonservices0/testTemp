package com.amazon.amazonpaymerchanturl.task;

import static com.amazon.amazonpaymerchanturl.constants.InvestigationStatus.COMPLIANT;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationStatus.COMPLIANT_TO_IN_REVIEW;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationStatus.IN_REVIEW;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationStatus.NON_COMPLIANT;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationStatus.NON_COMPLIANT_TO_IN_REVIEW;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_AUTO_MONITORING_URL;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_GET_WORKFLOW_STATUS_ERROR;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_INITIATE_URL_REVIEW_WORKFLOW_ERROR;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_INVESTIGATION_ID_NULL_IN_DOMAIN_VALIDATION_DDB;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_INVESTIGATION_STATUS_NULL_IN_DOMAIN_VALIDATION_DDB;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_NO_ENTRY_IN_DOMAIN_VALIDATION_DDB;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_NO_ENTRY_IN_URL_INVESTIGATION_DDB;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_NO_TASK_TOKEN;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_RESUME_URL_REVIEW_DUPLICATE_REQUEST;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_RESUME_URL_REVIEW_WORKFLOW_ERROR;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.URL_REVIEW_WORKFLOW_NOT_AVAILABLE_METRICS;
import static com.amazon.urlvendorreviewlib.constants.LibConstants.AMAZON_PAY_BUSINESS;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.constants.InvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.InvestigationType;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.UrlVendorReviewResponseInput;
import com.amazon.amazonpaymerchanturl.model.UrlVendorReviewScanSpecInput;
import com.amazon.urlvendorreviewmodel.model.ScanSpec;
import com.amazon.urlvendorreviewmodel.model.UrlSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

/**
 * Callback workflow determinator task to initate or resume workflow.
 */
@Log4j2
public class CallbackWorkflowDeterminatorTask {

    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final StepFunctionAdapter stepFunctionAdapter;
    private final Map<String, String> urlReviewWorkflowMap;
    private final ObjectMapper objectMapper;
    private final SQSAdapter sqsAdapter;
    private final String queueUrl;
    private final WeblabHelper weblabHelper;

    //CHECKSTYLE:SUPPRESS:ParameterNumber
    public CallbackWorkflowDeterminatorTask(final DomainValidationDDBAdapter domainValidationDDBAdapter,
                                            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                            final CloudWatchMetricsHelper cloudWatchMetricsHelper,
                                            final StepFunctionAdapter stepFunctionAdapter,
                                            final Map<String, String> urlReviewWorkflowMap,
                                            final ObjectMapper objectMapper,
                                            final SQSAdapter sqsAdapter,
                                            final String queueUrl,
                                            final WeblabHelper weblabHelper) {
        this.domainValidationDDBAdapter = domainValidationDDBAdapter;
        this.urlInvestigationDDBAdapter = urlInvestigationDDBAdapter;
        this.cloudWatchMetricsHelper = cloudWatchMetricsHelper;
        this.stepFunctionAdapter = stepFunctionAdapter;
        this.urlReviewWorkflowMap = urlReviewWorkflowMap;
        this.objectMapper = objectMapper;
        this.sqsAdapter = sqsAdapter;
        this.queueUrl = queueUrl;
        this.weblabHelper = weblabHelper;
    }
    //CHECKSTYLE:UNSUPPRESS:ParameterNumber

    /**
     * initateOrResumeURLReviewWorkflow determines if the workflow needs to be resumed or initiated or not.
     * A. If url is not yet reported by us (i.e. not onboarded to EverC) -> then don't process callback response.
     *
     * B. If url is reported by us (i.e. onboarded to EverC) -> then process callback response.
     *      1.) If entry present in DomainValidationDDB for clientReferenceGroupId, url.
     *          1.a) If auto monitoring scan
     *              i) If investigation status is In_Review/Compliant_To_In_Review/Non_Compliant_To_In_Review,
     *                  then log and publish metric, as we do not want to intiate or resume the workflow.
     *              ii) If investigation status is Compliant/Non_Compliant,
     *                  we update the domain validation DDB with investigation status to
     *                  Compliant_To_In_Review/Non_Compliant_To_In_Review accordingly.
     *                  And initiate URLReviewWorkflow.
     *          1.b) I) If entry present in UrlInvestigationDDB for investigation id and sub investigation status,
     *                  i) If task token present, then resume url vendor review workflow.
     *                  ii) If task token not present, then no-op log and publish a metric.
     *              II) if entry not present in UrlInvestigationDDB for investigation id and sub investigation status,
     *                  log and publish a metric.
     *      2.) If entry not present in DomainValidationDDB for clientReferenceGroupId, url,
     *          log and publish a metric.
     *
     * @param urlVendorReviewScanSpecInput  urlVendorReviewScanSpecInput
     */
    public void initateOrResumeURLReviewWorkflow(@NonNull final UrlVendorReviewScanSpecInput
                                                         urlVendorReviewScanSpecInput) {

        final List<ScanSpec> scanSpecList = urlVendorReviewScanSpecInput.getScanSpecList();
        scanSpecList.stream().forEach(scanSpec -> {
            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[WORKFLOW_DETERMINATOR_REQUEST] Initiate or resume url review workflow for {}", scanSpec);

            final String clientReferenceGroupId = scanSpec.getClientId();
            final String subInvestigationType = scanSpec.getScanType().getSubInvestigationType();
            final String scanId = scanSpec.getScanId();

            scanSpec.getScanItems().forEach((scanItemId, urlSpec) -> {
                final String normalizedUrl = urlSpec.getUrl();
                final ScanSpec scanSpecWithSingleUrl = constructScanSpecWithSingleUrl(scanSpec, scanItemId, urlSpec);
                log.info("Scanspec with single url {} ", scanSpecWithSingleUrl);

                if (Boolean.FALSE.equals(urlSpec.getReportedUrl())) {
                    /*
                    Note : MetricFilter syntax pattern should be updated in CDK package accordingly
                    if there is a change in Log message
                    */
                    log.info("[UN_REPORTED_AND_NOT_ONBORADED_URL] Not processing callback response " +
                                    "for clientReferenceGroupId: {} and url: {} " +
                            "as this url is not yet reported by us i.e. not onboarded to EverC",
                            clientReferenceGroupId, urlSpec.getUrl());
                    return;
                }
                try {
                    List<AmazonPayDomainValidationItem> amazonPayDomainValidationItemList =
                    domainValidationDDBAdapter.queryOnClientRefGrpIdNormalizedUrlIndex(
                            clientReferenceGroupId, normalizedUrl);

                    if (CollectionUtils.isEmpty(amazonPayDomainValidationItemList)) {
                        log.info("Entry not present in DomainValidationDDB for clientReferenceGroupId {}"
                                + " and normalizedUrl {} for scanId {}.", clientReferenceGroupId,
                                normalizedUrl, scanId);
                        cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                                EXECUTE_VENDOR_RESPONSE_NO_ENTRY_IN_DOMAIN_VALIDATION_DDB);
                        return;
                    }

                    //TODO : Remove this after deduping all the existing variantURL entries in DDB
                    /*
                    Convert the existing variantURL entries (for which Review was completed)
                    into a single Standardized Url entry for monitoring scan
                     */
                    if (isAutoMonitoringScan(subInvestigationType) &&
                            weblabHelper.isWeblabDialedUpForDedupingVariantUrls(clientReferenceGroupId)) {
                        amazonPayDomainValidationItemList = dedupeExistingCompletedReviewURls(
                                amazonPayDomainValidationItemList, clientReferenceGroupId, normalizedUrl);

                    }

                    amazonPayDomainValidationItemList.forEach(domainValidationItem -> {
                        log.info("Executing callback workflow determinator for SubInvType {} ," +
                                        "clientReferenceGroupId {}, url {} and normalizedUrl {} for scanId {}",
                                subInvestigationType, clientReferenceGroupId,
                                domainValidationItem.getUrl(), normalizedUrl, scanId);
                        if (Boolean.FALSE.equals(domainValidationItem.getIsActive())) {
                            log.info("Review url: {} for clientReferenceGroupId: {} is currently inactive, hence "
                                    + "ignoring the processing of callback response for subInvestigationType:{}",
                                    domainValidationItem.getUrl(), domainValidationItem.getClientReferenceGroupId(),
                                    subInvestigationType);
                            return;
                        }

                        if (isAutoMonitoringScan(subInvestigationType)) {
                            if (Objects.nonNull(domainValidationItem.getInvestigationStatus())) {
                                autoMonitoringScanTask(domainValidationItem, scanId, scanSpecWithSingleUrl,
                                        urlVendorReviewScanSpecInput.getRetryCount());
                            } else {
                                log.info("Investigation status is null for clientReferenceGroupId {}, url {}"
                                        + " and normalizedUrl {} for scanId {}", clientReferenceGroupId,
                                        domainValidationItem.getUrl(), normalizedUrl, scanId);
                                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                                        EXECUTE_VENDOR_RESPONSE_INVESTIGATION_STATUS_NULL_IN_DOMAIN_VALIDATION_DDB);
                            }
                        } else {
                            if (Objects.nonNull(domainValidationItem.getInvestigationId())) {
                                autoLightOrHeavyWeightScanTask(domainValidationItem,
                                        subInvestigationType, scanId, scanSpecWithSingleUrl,
                                        urlVendorReviewScanSpecInput.getRetryCount());
                            } else {
                                log.info("InvestigationId is null for clientReferenceGroupId {}, url {}"
                                         + " and normalizedUrl {} for scanId {}", clientReferenceGroupId,
                                        domainValidationItem.getUrl(), normalizedUrl, scanId);
                                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                                        EXECUTE_VENDOR_RESPONSE_INVESTIGATION_ID_NULL_IN_DOMAIN_VALIDATION_DDB);
                            }
                        }
                    });
                } catch (Exception e) {
                    if (e instanceof AmazonPayDomainValidationDAORetryableException) {
                        log.error("DDB Exception received.", e);
                        urlVendorReviewScanSpecInput.setScanSpecList(Collections.singletonList(scanSpecWithSingleUrl));
                        sendMessageToErrorQueue(urlVendorReviewScanSpecInput);
                    } else {
                        log.error("Exception received during initateOrResumeURLReviewWorkflow", e);
                    }
                }
            });
        });
    }

    //TODO : Remove the below function after de-duping the existing variantURls in DDB
    private List<AmazonPayDomainValidationItem> dedupeExistingCompletedReviewURls(
            List<AmazonPayDomainValidationItem> amazonPayDomainValidationItemList,
            final String clientRefGrpId, final String normalizedUrl) {
        AmazonPayDomainValidationItem standardizedUrlItem = null;
        List<AmazonPayDomainValidationItem> completedReviewItems = new ArrayList<>();
        List<AmazonPayDomainValidationItem> urlItemsTobeProcessed = new ArrayList<>();

        for (AmazonPayDomainValidationItem item : amazonPayDomainValidationItemList) {
            if (StringUtils.equals(item.getUrl(), item.getNormalizedUrl())) {
                standardizedUrlItem = item;
            } else if (isUrlReviewCompleted(item.getInvestigationStatus())) {
                completedReviewItems.add(item);
            } else {
                urlItemsTobeProcessed.add(item);
            }
        }

        if (isDedupingNotRequired(completedReviewItems, standardizedUrlItem, clientRefGrpId, normalizedUrl)) {
            return amazonPayDomainValidationItemList;
        }

        if (!isAllReviewcompletedUrlsHaveSameInvestigationStatus(completedReviewItems)) {
            log.info("[REVIEW_COMPLETED_VARIANT_URLS] All completed review url items have different investigation" +
                    " status. Proceeding Execution of vendor Response as it is without de-duping variantUrl flow");
            return dedupeUrlsHavingDifferentInvestigationStatus(amazonPayDomainValidationItemList,
                    completedReviewItems, urlItemsTobeProcessed, standardizedUrlItem);
        }

        if (isStandardizedUrlAndVariantUrlsHaveDifferentStatus(standardizedUrlItem, completedReviewItems)) {
            log.info("[STANDARD_URL_STATUS_MISMATCH] Not De-duping the existing VariantUrls to existing " +
                "standardUrl entry as review completed variantUrl status is different from standardizedUrl status.");
            urlItemsTobeProcessed.add(standardizedUrlItem);
            return urlItemsTobeProcessed;
        }
        return mergeVariantUrls(completedReviewItems, urlItemsTobeProcessed, standardizedUrlItem);
    }

    private List<AmazonPayDomainValidationItem> dedupeUrlsHavingDifferentInvestigationStatus(
                            List<AmazonPayDomainValidationItem> amazonPayDomainValidationItemList,
                            List<AmazonPayDomainValidationItem> completedReviewItems,
                            List<AmazonPayDomainValidationItem> urlItemsTobeProcessed,
                            AmazonPayDomainValidationItem standardizedUrlItem) {
        if (Objects.isNull(standardizedUrlItem)) {
            log.info("[DIFFERENT_INVESTIGATION_STATUS]Found different investigation status for the variant Urls " +
                    "of clientRefGrp id : {} and NormalizedUrl : {}",
                    completedReviewItems.get(0).getClientReferenceGroupId(),
                    completedReviewItems.get(0).getNormalizedUrl());
        } else if (standardizedUrlItem.getInvestigationStatus().equals(IN_REVIEW.getInvestigationStatus())) {
            return mergeVariantUrls(completedReviewItems, urlItemsTobeProcessed, standardizedUrlItem);
        }
        return amazonPayDomainValidationItemList;
    }

    private List<AmazonPayDomainValidationItem> mergeVariantUrls(
            List<AmazonPayDomainValidationItem> completedReviewItems,
            List<AmazonPayDomainValidationItem> urlItemsTobeProcessed,
            AmazonPayDomainValidationItem standardizedUrlItem) {

        Set<String> variantUrlsTobeMerged = completedReviewItems.stream()
                .map(AmazonPayDomainValidationItem::getUrl).collect(Collectors.toSet());


        if (Objects.nonNull(standardizedUrlItem) && Objects.isNull(standardizedUrlItem.getVariantURLs())) {
            //The existing Standard URl is one of the variantURls for which the review is completed.
            variantUrlsTobeMerged.add(standardizedUrlItem.getUrl());
        }

        //create a new standardized URL Item with updated VariantUrL List
        AmazonPayDomainValidationItem newStandardizedItem = (Objects.isNull(standardizedUrlItem)) ?
                createStandardizedUrlItemFromVariantUrls(completedReviewItems.get(0), variantUrlsTobeMerged) :
                createStandardizedUrlItemFromVariantUrls(standardizedUrlItem, variantUrlsTobeMerged);

        domainValidationDDBAdapter.createOrUpdateEntryWithVariantUrlSetAppend(newStandardizedItem);
        domainValidationDDBAdapter.deleteUrlEntries(completedReviewItems);
        urlItemsTobeProcessed.add(newStandardizedItem);
        return urlItemsTobeProcessed;
    }

    /**
     * If investigation status is In_Review/Compliant_To_In_Review/Non_Compliant_To_In_Review,
     * for auto monitoring scan of a url, it is a no-op, we log and publish a metric.
     * @param investigationStatus      investigation status
     * @param url                      url
     * @param scanId                   scan id
     * @return                         true/false based on url investigation status
     */
    private boolean isAutoMonitoringScanUrlUnderReview(final String workflowId, final String investigationStatus,
                                                       final String url, final String scanId) {
        try {
            if (StringUtils.isNotEmpty(workflowId)
                    && StringUtils.equals(ExecutionStatus.RUNNING.toString(),
                    stepFunctionAdapter.getWorkflowStatus(workflowId))) {
                log.info("Previous step function workflowId: {} for url: {} status is RUNNING, hence url "
                                + "under review.", workflowId, url);
                return true;
            }
        } catch (AmazonPayMerchantURLBaseException e) {
            log.error("Exception received while fetching the workflow status for workflowId: {} and url: {}",
                    workflowId, url, e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    EXECUTE_VENDOR_RESPONSE_GET_WORKFLOW_STATUS_ERROR);
        }

        if (StringUtils.equals(InvestigationStatus.IN_REVIEW.getInvestigationStatus(), investigationStatus)
                || StringUtils.equals(InvestigationStatus.COMPLIANT_TO_IN_REVIEW.getInvestigationStatus(),
                investigationStatus)
                || StringUtils.equals(InvestigationStatus.NON_COMPLIANT_TO_IN_REVIEW.getInvestigationStatus(),
                investigationStatus)) {
            log.info("Auto monitoring scan {} for url {} is in {}, hence no op.", scanId, url, investigationStatus);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    EXECUTE_VENDOR_RESPONSE_AUTO_MONITORING_URL + investigationStatus);
            return true;
        }
        return false;
    }

    /**
     * autoMonitoringScanTask checks based on investigation status for initiating URLReviewWorkflow or not.
     * @param domainValidationItem    domain validation ddb item.
     * @param scanId                  scan id.
     * @param scanSpecWithSingleUrl   scan spec with single url.
     * @param retryCount              retry count.
     */
    private void autoMonitoringScanTask(final AmazonPayDomainValidationItem domainValidationItem, final String scanId,
                                        final ScanSpec scanSpecWithSingleUrl, final Long retryCount) {
        final String investigationStatus = domainValidationItem.getInvestigationStatus();
        final String url = domainValidationItem.getUrl();
        final String workflowId = domainValidationItem.getInvestigationId();

        if (!isAutoMonitoringScanUrlUnderReview(workflowId, investigationStatus, url, scanId)) {
            String updateInvestigationStatus = null;

            if (StringUtils.equals(InvestigationStatus.COMPLIANT.getInvestigationStatus(), investigationStatus)) {
                updateInvestigationStatus = InvestigationStatus.COMPLIANT_TO_IN_REVIEW.getInvestigationStatus();
            } else if (StringUtils.equals(InvestigationStatus.NON_COMPLIANT.getInvestigationStatus(),
                    investigationStatus)) {
                updateInvestigationStatus = InvestigationStatus.NON_COMPLIANT_TO_IN_REVIEW.getInvestigationStatus();
            } else {
                log.info("Unknown/New investigation status : {} detected from scanId {}.", investigationStatus, scanId);
            }

            if (Objects.nonNull(updateInvestigationStatus)) {
                domainValidationItem.setInvestigationStatus(updateInvestigationStatus);
                if (!domainValidationDDBAdapter.updateEntryIfInvStatusIsNotUnderReview(domainValidationItem)) {
                    log.info("ClientReferenceGroupId {} URL {} already updated.",
                            domainValidationItem.getClientReferenceGroupId(), url);
                    return;
                }
                initiateURLReviewWorkflow(scanSpecWithSingleUrl, investigationStatus,
                        domainValidationItem.getClientInfo(), url, retryCount);
            }
        }
    }

    /**
     * autoLightOrHeavyWeightScanTask does UrlInvestigationDDB look up for the subInvestigation token
     * to resume URLReviewWorkflow or not.
     * @param domainValidationItem       domain validation item
     * @param subInvestigationType       sub investigation type
     * @param scanId                     scan id
     * @param scanSpecWithSingleUrl      scan spec with single url
     * @param retryCount                 retry count
     */
    private void autoLightOrHeavyWeightScanTask(final AmazonPayDomainValidationItem domainValidationItem,
                                                final String subInvestigationType,
                                                final String scanId,
                                                final ScanSpec scanSpecWithSingleUrl,
                                                final Long retryCount) {
        final String investigationId = domainValidationItem.getInvestigationId();
        final String url = domainValidationItem.getUrl();

        final UrlInvestigationItem urlInvestigationItem =
                urlInvestigationDDBAdapter.loadEntry(investigationId, subInvestigationType);

        if (Objects.nonNull(urlInvestigationItem)) {
            log.info("UrlInvestigationDDB entry for InvestigationId {} and SubInvestigationType {} is"
                            + " {} for scanId {}", investigationId, subInvestigationType, urlInvestigationItem, scanId);

            if (Objects.nonNull(urlInvestigationItem.getSubInvestigationTaskToken())) {
                log.info("Resuming url review workflow of SubInvType {} with task token {} for url {} " +
                                "and clientRefGrpId {} for scanId {}",
                        subInvestigationType, urlInvestigationItem.getSubInvestigationTaskToken(), url,
                        domainValidationItem.getClientReferenceGroupId(), scanId);
                resumeURLReviewWorkflow(urlInvestigationItem, scanSpecWithSingleUrl, url, retryCount);
            } else {
                log.info("Token is not present for url {} and clientRefGrpId {} with investigation id {} and " +
                        "sub investigation type {} from scanId {} ", url,
                        domainValidationItem.getClientReferenceGroupId(), investigationId,
                        subInvestigationType, scanId);
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                        EXECUTE_VENDOR_RESPONSE_NO_TASK_TOKEN);
            }
        } else {
            log.info("Entry not present in UrlInvestigationDDB for InvestigationId {} and "
                            + "SubInvestigationType {} for url {} and clientRefGrpId {} for scanId {}",
                    investigationId, subInvestigationType, url,
                    domainValidationItem.getClientReferenceGroupId(), scanId);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    EXECUTE_VENDOR_RESPONSE_NO_ENTRY_IN_URL_INVESTIGATION_DDB);
        }
    }

    /**
     * 1. Initiate url review workflow for a url.
     * 2. Update DomainValidation DDB with workflowId.
     * 3. If there is a workflow failure, then update the investigation status to the previous state.
     *
     * TODO : Currently hard-coding to AmazonPay business, need to revisit and handle this accordingly for all flows.
     *
     * @param scanSpecWithSingleUrl                ScanSpec for a url.
     * @param investigationStatusBeforeDBUpdate    Inv status before db update.
     * @param clientCustomInformation              Client custom info.
     * @param url                                  Url.
     * @param retryCount                           Retry count.
     */
    private void initiateURLReviewWorkflow(final ScanSpec scanSpecWithSingleUrl,
                                           final String investigationStatusBeforeDBUpdate,
                                           final String clientCustomInformation,
                                           final String url,
                                           final Long retryCount) {
        final UrlVendorReviewResponseInput urlVendorReviewResponseInput = UrlVendorReviewResponseInput.builder()
                .clientReferenceGroupId(scanSpecWithSingleUrl.getClientId())
                .reviewURL(url)
                .clientCustomInformation(clientCustomInformation)
                .scanSpec(scanSpecWithSingleUrl)
                .investigationType(InvestigationType.PERIODIC_INVESTIGATION.getInvestigationType())
                .build();
        try {
            startURLReviewWorkflow(objectMapper.writeValueAsString(urlVendorReviewResponseInput),
                    scanSpecWithSingleUrl.getClientId(), url);
        } catch (Exception e) {
            log.info("Exception encountered while executing UrlReviewWorkflow for URL: {}, ScanSpec : {}",
                    url, scanSpecWithSingleUrl, e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    EXECUTE_VENDOR_RESPONSE_INITIATE_URL_REVIEW_WORKFLOW_ERROR);
            domainValidationDDBAdapter.createOrUpdateEntry(AmazonPayDomainValidationItem.builder()
                    .clientReferenceGroupId(scanSpecWithSingleUrl.getClientId())
                    .url(url)
                    .investigationStatus(investigationStatusBeforeDBUpdate)
                    .build());
            final UrlVendorReviewScanSpecInput urlVendorReviewScanSpecInput = buildUrlVendorReviewScanSpecInput(
                    scanSpecWithSingleUrl, retryCount);
            sendMessageToErrorQueue(urlVendorReviewScanSpecInput);
        }

    }

    private ScanSpec constructScanSpecWithSingleUrl(final ScanSpec scanSpec, final String scanItemId,
                                                    final UrlSpec urlSpec) {
        Map<String, UrlSpec> scanItems = new HashMap<String, UrlSpec>();
        scanItems.put(scanItemId, urlSpec);

        return ScanSpec.builder()
                .scanId(scanSpec.getScanId())
                .scanType(scanSpec.getScanType())
                .createdDate(scanSpec.getCreatedDate())
                .deliveryDate(scanSpec.getDeliveryDate())
                .clientId(scanSpec.getClientId())
                .riskSpec(scanSpec.getRiskSpec())
                .scanItems(scanItems)
                .scanStatus(scanSpec.getScanStatus())
                .scanPath(scanSpec.getScanPath())
                .build();
    }

    /**
     * Start a StepFunction Workflow with the given payload
     * @param payload payload
     * @param clientReferenceGroupId clientReferenceGroupId
     * @param url url
     */
    private void startURLReviewWorkflow(final String payload, final String clientReferenceGroupId,
                                        final String url) {
        final String stepFunctionArn = urlReviewWorkflowMap.get(AMAZON_PAY_BUSINESS);
        if (StringUtils.isBlank(stepFunctionArn)) {
            log.info("UrlReview workflow not defined for {}", AMAZON_PAY_BUSINESS);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(URL_REVIEW_WORKFLOW_NOT_AVAILABLE_METRICS);
            return;
        }

        String workflowId = null;

        workflowId = stepFunctionAdapter.startWorkflow(
                stepFunctionArn, payload);

        /*
        Note : MetricFilter syntax pattern should be updated in CDK package accordingly
        if there is a change in Log message
        */
        log.info("[STARTED_NEW_WORKFLOW] Started UrlReviewWorkflow for Periodic_Investigation : {}, " +
                        "clientRefGrpId:{} and url: {}",
                workflowId, clientReferenceGroupId, url);

        log.info("Updating workflow id {} in Domain Validation table.", workflowId);
        domainValidationDDBAdapter.createOrUpdateEntry(AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .investigationId(workflowId)
                .caseCreationTime(Instant.now().toEpochMilli())
                .build());
    }

    private void resumeURLReviewWorkflow(final UrlInvestigationItem urlInvestigationItem,
                                         final ScanSpec scanSpecWithSingleUrl,
                                         final String url,
                                         final Long retryCount) {
        final String subInvestigationToken = urlInvestigationItem.getSubInvestigationTaskToken();

        try {
            final boolean isStepFunctionResumed = stepFunctionAdapter.resumeWorkflow(subInvestigationToken,
                    objectMapper.writeValueAsString(scanSpecWithSingleUrl));

            if (!isStepFunctionResumed) {
                log.info("TaskToken [{}] does not exist anymore for scan {} ",
                            subInvestigationToken, scanSpecWithSingleUrl);
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                        EXECUTE_VENDOR_RESPONSE_RESUME_URL_REVIEW_DUPLICATE_REQUEST);
                return;
            }

            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[RESUME_WORKFLOW] Resumed UrlReviewWorkflow successfully for subInvestigationType {} ," +
                            "clientRefGrpId_url: {}", urlInvestigationItem.getSubInvestigationType(),
                    urlInvestigationItem.getClientReferenceGroupIdUrl());
        } catch (Exception e) {
            log.info("Exception encountered while resuming UrlReviewWorkflow for URL: {}, ScanSpec : {}",
                    url, scanSpecWithSingleUrl, e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    EXECUTE_VENDOR_RESPONSE_RESUME_URL_REVIEW_WORKFLOW_ERROR);
            final UrlVendorReviewScanSpecInput urlVendorReviewScanSpecInput = buildUrlVendorReviewScanSpecInput(
                    scanSpecWithSingleUrl, retryCount);
            sendMessageToErrorQueue(urlVendorReviewScanSpecInput);
            return;
        }
    }

    private void sendMessageToErrorQueue(final UrlVendorReviewScanSpecInput urlVendorReviewScanSpecInput) {
        try {
            sqsAdapter.sendMessage(objectMapper.writeValueAsString(urlVendorReviewScanSpecInput), queueUrl);
        } catch (Exception e) {
            log.info("Failed to send message urlVendorReviewScanSpecInput {} to error queue.",
                    urlVendorReviewScanSpecInput, e);
        }
    }

    private boolean isAutoMonitoringScan(final String subInvestigationType) {
        return StringUtils.equals(SubInvestigationType.AUTO_MONITORING.getSubInvestigationType(),
                subInvestigationType);
    }

    private UrlVendorReviewScanSpecInput buildUrlVendorReviewScanSpecInput(final ScanSpec scanSpec,
                                                                           final Long retryCount) {
        return UrlVendorReviewScanSpecInput.builder()
                .scanSpecList(Collections.singletonList(scanSpec))
                .retryCount(retryCount)
                .build();
    }

    /*
    This method returns true if variantUrls can't be de-duped, otherwise false
     */
    private boolean isDedupingNotRequired(final List<AmazonPayDomainValidationItem> completedReviewItems,
                                          final AmazonPayDomainValidationItem standardizedUrlItem,
                                          final String clientRefGrpId, final String normalizedUrl) {
        if (CollectionUtils.isEmpty(completedReviewItems)) {
            log.info("No Url entries found with completed review status(Compliant/Non_Compliant for" +
                    "clientReferenceGroupId: {} and Normalized url: {}", clientRefGrpId, normalizedUrl);
        } else if (isExistingInReviewVariantUrlSimilarToStandardizedURL(standardizedUrlItem)) {
            log.info("There is an existing variantURL same as Standardized URL, which is In_Review. " +
                    "We cannot merge variant urls to existing StandardizedUrl as it runs with old workflow state");
        } else if (!isAllReviewCompletedUrlsHaveSameActiveStatus(completedReviewItems)) {
            log.info("[URL_ACTIVE_STATUS_MISMATCH] Found different isActive status for reviewCompleted urls. " +
                    "Proceeding Execution of vendor Response as it is without de-duping variantUrl flow" +
                    "for ClientReferenceGroupId : {} and url {}",
                    completedReviewItems.get(0).getClientReferenceGroupId(),
                    completedReviewItems.get(0).getNormalizedUrl());
        } else {
            //CompletedReviewUrl items have same status which can be de-deduped
            return false;
        }
        return true;
    }

    private boolean isStandardizedUrlAndVariantUrlsHaveDifferentStatus(
            AmazonPayDomainValidationItem standardizedUrlItem,
            List<AmazonPayDomainValidationItem> completedReviewItems) {
        return Objects.nonNull(standardizedUrlItem) &&
                (!canVariantUrlsBeMergedWithStandardUrl(
                completedReviewItems.get(0).getInvestigationStatus(), standardizedUrlItem.getInvestigationStatus()) ||
                !Objects.equals(completedReviewItems.get(0).getIsActive(), standardizedUrlItem.getIsActive()));
    }

    private boolean isAllReviewcompletedUrlsHaveSameInvestigationStatus(
            List<AmazonPayDomainValidationItem> completedReviewItems) {
        return completedReviewItems.stream().allMatch(
                item -> item.getInvestigationStatus().equals(completedReviewItems.get(0).getInvestigationStatus()));
    }

    private boolean isAllReviewCompletedUrlsHaveSameActiveStatus(
            List<AmazonPayDomainValidationItem> completedReviewItems) {
        return completedReviewItems.stream().allMatch(
                item -> (Objects.equals(item.getIsActive(), completedReviewItems.get(0).getIsActive())));
    }

    private boolean isExistingInReviewVariantUrlSimilarToStandardizedURL(
            AmazonPayDomainValidationItem standardizedUrlItem) {
        return Objects.nonNull(standardizedUrlItem) && Objects.isNull(standardizedUrlItem.getVariantURLs())
                && isUrlUnderReview(standardizedUrlItem.getInvestigationStatus());
    }

    /**
     * Return true, if the existing review completed variantURL can be added to Standardized URl entry
     *  Variant URls will be added to the "VariantURLs" attribute of Standardized URL entry -
     * a. if StandardizedURL status is "In_Review" and VariantURL status either "Compliant"/"Non_Compliant"
     * b. if StandardizedURL status is "Compliant"/"Compliant_To_In_Review and VariantURL status is "Compliant"
     * c. if StandardizedURL status is "Non_Compliant"/"Non_Compliant_To_InReview & VariantURL status is "Non_Compliant"
     * @param variantUrlItemStatus existing variantURL investigation status
     * @param standardardizedUrlItemStatus existing standardizedUrl investigation status
     * @return true or false
     */
    public boolean canVariantUrlsBeMergedWithStandardUrl(final String variantUrlItemStatus,
                                                         final String standardardizedUrlItemStatus) {

        return ((standardardizedUrlItemStatus.equals(COMPLIANT.getInvestigationStatus()) ||
                standardardizedUrlItemStatus.equals(COMPLIANT_TO_IN_REVIEW.getInvestigationStatus()))
                && variantUrlItemStatus.equals(COMPLIANT.getInvestigationStatus())) ||

                ((standardardizedUrlItemStatus.equals(NON_COMPLIANT.getInvestigationStatus()) ||
                        standardardizedUrlItemStatus.equals(NON_COMPLIANT_TO_IN_REVIEW.getInvestigationStatus()))
                        && variantUrlItemStatus.equals(NON_COMPLIANT.getInvestigationStatus())) ||

                standardardizedUrlItemStatus.equals(IN_REVIEW.getInvestigationStatus());
    }

    private boolean isUrlReviewCompleted(final String investigationStatus) {
        return StringUtils.equals(investigationStatus, COMPLIANT.getInvestigationStatus()) ||
                StringUtils.equals(investigationStatus, NON_COMPLIANT.getInvestigationStatus());
    }

    /**
     * This method checks if the URl is under review or not
     * @param investigationStatus investigatiion status of url
     * @return return true if Url is under review , else false
     */
    public boolean isUrlUnderReview(final String investigationStatus) {
        return (StringUtils.equals(InvestigationStatus.IN_REVIEW.getInvestigationStatus(), investigationStatus)
                || StringUtils.equals(InvestigationStatus.COMPLIANT_TO_IN_REVIEW.getInvestigationStatus(),
                investigationStatus)
                || StringUtils.equals(InvestigationStatus.NON_COMPLIANT_TO_IN_REVIEW.getInvestigationStatus(),
                investigationStatus));
    }

    /*
    This will create a new DDB Item with StandardizedUrl from the list of
    VariantUrlItems( where the url review is completed and all the urls have same investigation status)
     */
    private AmazonPayDomainValidationItem createStandardizedUrlItemFromVariantUrls(
            final AmazonPayDomainValidationItem item, Set<String> variantUrls) {
        return AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(item.getClientReferenceGroupId())
                .normalizedUrl(item.getNormalizedUrl())
                .clientInfo(item.getClientInfo())
                .investigationStatus(item.getInvestigationStatus())
                .urlSource(item.getUrlSource())
                .urlType(item.getUrlType())
                .reviewInfo(item.getReviewInfo())
                .investigationId(item.getInvestigationId())
                .subInvestigationType(item.getSubInvestigationType())
                .isActive(item.getIsActive() == null || item.getIsActive())
                .url(item.getNormalizedUrl())
                .variantURLs(variantUrls)
                .build();
    }
}
