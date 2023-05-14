package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.constants.InvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.InvestigationType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.ProcessInvestigationRequest;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.amazonpaymerchanturl.utils.URLCaseSensitivityConvertorUtil;
import com.amazon.amazonpaymerchanturl.utils.URLStandardizeUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.AMAZON_PAY_BUSINESS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW_INVALID_TASK_TOKEN;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_MESSAGE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW_SUCCESS_MESSAGE;

/**
 * ExecuteManualResponseWorkflowHandler fetch the task token from domainValidation DB.
 * and then resume the workflow.
 */
@RequiredArgsConstructor
@Log4j2
public class ExecuteManualResponseWorkflowHandler {

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final AmazonSQS sqsClient;
    private final StepFunctionAdapter stepFunctionAdapter;
    private final Map<String, String> urlReviewWorkflowMap;
    private final SQSAdapter sqsAdapter;
    private final String executeManualResponseWorkflowDlqUrl;
    private final WeblabHelper weblabHelper;

    public ExecuteManualResponseWorkflowHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.domainValidationDDBAdapter = lambdaComponent.providesDomainValidationDDBAdapter();
        this.urlInvestigationDDBAdapter = lambdaComponent.providesUrlInvestigationDDBAdapter();
        this.sqsClient = lambdaComponent.providesSQSServiceClient();
        this.stepFunctionAdapter = lambdaComponent.providesStepFunctionAdapter();
        this.urlReviewWorkflowMap = lambdaComponent.providesUrlReviewWorkflowMap();
        this.sqsAdapter = lambdaComponent.providesSQSAdapter();
        this.executeManualResponseWorkflowDlqUrl = lambdaComponent.providesExecuteManualResponseWorkflowDlqUrl();
        this.weblabHelper = lambdaComponent.provideWeblabHelper();
    }

    /**
     * handle request is the entry point to execute the workflow.
     *
     * @param inputStream input for the lambda.
     * @param outputStream output for the lambda.
     * @param context context.
     */
    public void handleRequest(@NonNull final InputStream inputStream, @NonNull final OutputStream outputStream,
                              @NonNull final Context context) {
        final String lambdaName = Lambda.lambdaFunctionName(context);
        log.info(lambdaName + " lambda invoked");

        ProcessInvestigationRequest processInvestigationRequest;
        String inputString;

        try {
            processInvestigationRequest = objectMapper.readValue(inputStream, ProcessInvestigationRequest.class);
            inputString = objectMapper.writeValueAsString(processInvestigationRequest);
        } catch (Exception e) {
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_METRICS);
            log.error("Exception: {} is reported in lambda: {}", e, lambdaName);
            final String responseDetails = "input parsing failed.";
            sendLambdaResponse(FAILED_STATUS_CODE, outputStream, lambdaName, responseDetails);
            return;
        }

        log.info("[LAMBDA_INPUT] {} is called with input: {}", lambdaName, processInvestigationRequest);

        int responseStatusCode = FAILED_STATUS_CODE;
        try {
            responseStatusCode = startOrResumeWorkflow(processInvestigationRequest, inputString);
        } catch (AmazonPayMerchantURLNonRetryableException | AmazonPayDomainValidationDAONonRetryableException e) {
            log.error("Non Retryable Exception occur while processing request: {}", processInvestigationRequest, e);
        } catch (AmazonPayMerchantURLRetryableException | AmazonPayDomainValidationDAORetryableException e) {
            log.info("Retryable Exception occur while processing request: {}, hence sending to dlq",
                    processInvestigationRequest, e);
            sqsAdapter.sendMessage(inputString, executeManualResponseWorkflowDlqUrl);
        }

        String responseDetails = EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_MESSAGE;
        if (responseStatusCode == SUCCESS_STATUS_CODE) {
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(EXECUTE_MANUAL_RESPONSE_WORKFLOW_SUCCESS_METRICS);
            responseDetails = EXECUTE_MANUAL_RESPONSE_WORKFLOW_SUCCESS_MESSAGE;
        } else {
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_METRICS);
        }

        sendLambdaResponse(responseStatusCode, outputStream, lambdaName, responseDetails);
    }

    /**
     * * In this method we will start or resume the workflow using urlInvestigation ddb attributes
     *
     * Case1: When step function workflow is timed out
     *    Start the new workflow and update domainValidation DDB entry with new investigation id.
     *
     * Case2: When subInvestigationId is Present in UrlInvestigation DDB
     *    Fetch SubInvestigationTaskToken from DDB and resume workFlow
     *
     * Case3: When subInvestigationId is not Present in UrlInvestigation DDB then execute the Appeal flow.
     *    Step1: Fetch existing InvestigationStatus from DomainValidation DDB.
     *    Step2: If investigation Status is Compliant or Non_Compliant,
     *    update DomainValidation DDB entry and start new workFlow, else send the message to DLQ.
     */
    private int startOrResumeWorkflow(final ProcessInvestigationRequest processInvestigationRequest,
                                      final String inputString) {
        if (Objects.isNull(processInvestigationRequest.getCaseId())) {
            log.info("Invalid input, case id is null for clientReferenceGroupId: {}, url: {}",
                    processInvestigationRequest.getClientReferenceGroupId(), processInvestigationRequest.getUrl());
            return FAILED_STATUS_CODE;
        }

        int responseStatusCode = SUCCESS_STATUS_CODE;
        // Fetching Latest UrlInvestigationItem for given SubInvestigationId
        final UrlInvestigationItem urlInvestigationItem
                = getUrlInvestigationItemForGivenSubInvestigationId(processInvestigationRequest.getCaseId())
                .orElse(null);
        if (Objects.nonNull(urlInvestigationItem) && isWorkflowFailed(urlInvestigationItem.getInvestigationId())) {
            log.info("Workflow with workflowId: {} for clientReferenceGroupIdUrl: {} is failed, hence starting "
                            + "the new workflow", urlInvestigationItem.getInvestigationId(),
                    urlInvestigationItem.getClientReferenceGroupIdUrl());
            startNewWorkFlow(processInvestigationRequest);
        } else if (Objects.nonNull(urlInvestigationItem)
                && Objects.nonNull(urlInvestigationItem.getSubInvestigationTaskToken())) {
            log.info("subInvestigationTaskToken: {} found in urlInvestigation DDB for ClientReferenceGroupId:"
                            + " {} and url: {}, hence resuming investigation workflow",
                    urlInvestigationItem.getSubInvestigationTaskToken(),
                    processInvestigationRequest.getClientReferenceGroupId(), processInvestigationRequest.getUrl());
            responseStatusCode = resumeWorkflow(urlInvestigationItem.getSubInvestigationTaskToken(), inputString);
        } else {
            log.info("UrlInvestigation ddb entry not found for caseId: {}, hence starting the appeal flow "
                    + "execution", processInvestigationRequest.getCaseId());
            responseStatusCode = executeAppealFlow(processInvestigationRequest);
        }
        return responseStatusCode;
    }

    /**
     * This method will return subInvestigationToken token corresponding to subInvestigationId
     * from DomainValidation DDB Table.
     */
    private Optional<UrlInvestigationItem> getUrlInvestigationItemForGivenSubInvestigationId(final Long caseId) {
        List<UrlInvestigationItem> urlInvestigationItemList;
        urlInvestigationItemList = urlInvestigationDDBAdapter
                .queryOnSubInvestigationIdAsSecondaryIndex(String.valueOf(caseId));

        // Fetch latest SubInvestigationToken corresponding to given CaseId.
        return urlInvestigationItemList.stream()
                .max(Comparator.comparing(UrlInvestigationItem::getReviewStartTime));
    }

    /**
     * This method will continue the manual response workflow execution based on InvestigationStatus.
     */
    private int executeAppealFlow(final ProcessInvestigationRequest processInvestigationRequest) {
        //Step1: Fetch existing InvestigationStatus from DomainValidation DDB.
        final String clientReferenceGroupId = processInvestigationRequest.getClientReferenceGroupId();

        //ToDo : Remove variantURL DB entry check after de-duping the DB with standardizedUrl
        String reviewUrl = getLowerCaseUrl(processInvestigationRequest.getUrl());
        AmazonPayDomainValidationItem amazonPayDomainValidationItem
                = domainValidationDDBAdapter.loadEntry(clientReferenceGroupId, reviewUrl);
        if (isUrlItemWithInvalidStatusOrInActive(amazonPayDomainValidationItem)) {
            log.info("Invalid DDB Entry for clientReferenceGroupId: {} and url:{}.",
                    clientReferenceGroupId, reviewUrl);
            if (weblabHelper.isWeblabDialedUpForDedupingVariantUrls(
                    processInvestigationRequest.getClientReferenceGroupId())) {
                final String standardizedUrl = URLStandardizeUtil.standardize(reviewUrl);
                log.info("Looking for StandardizedUrl entry in DDB:{}", standardizedUrl);
                amazonPayDomainValidationItem
                        = domainValidationDDBAdapter.loadEntry(clientReferenceGroupId, standardizedUrl);
                reviewUrl = standardizedUrl;
                processInvestigationRequest.setUrl(standardizedUrl);
            }
            if (isUrlItemWithInvalidStatusOrInActive(amazonPayDomainValidationItem)) {
                return FAILED_STATUS_CODE;
            }
        }

        //Step2: If investigation Status is Compliant or Non_Compliant, update DDB entry and start new workFlow,
        // else send the message to DLQ
        final String newInvestigationStatus;
        if (StringUtils.equals(amazonPayDomainValidationItem.getInvestigationStatus(),
                InvestigationStatus.COMPLIANT.getInvestigationStatus())) {
            newInvestigationStatus = InvestigationStatus.COMPLIANT_TO_IN_REVIEW.getInvestigationStatus();
        } else if (StringUtils.equals(amazonPayDomainValidationItem.getInvestigationStatus(),
                InvestigationStatus.NON_COMPLIANT.getInvestigationStatus())) {
            newInvestigationStatus = InvestigationStatus.NON_COMPLIANT_TO_IN_REVIEW.getInvestigationStatus();
        } else {
            log.info("InvestigationStatus received: {}, sending it to DLQ",
                    amazonPayDomainValidationItem.getInvestigationStatus());
            throw new AmazonPayMerchantURLRetryableException("wrong investigation status received: "
                    + amazonPayDomainValidationItem.getInvestigationStatus());
        }

        log.info("InvestigationStatus received: {}, updating status to {} in DomainValidation DDB",
                amazonPayDomainValidationItem.getInvestigationStatus(), newInvestigationStatus);

        updateInvestigationStatusInDomainValidationDDB(clientReferenceGroupId, reviewUrl, newInvestigationStatus);
        startNewWorkFlow(processInvestigationRequest);
        return SUCCESS_STATUS_CODE;
    }

    /**
     * Check if Url Item is inavalid or not
     * Returns False if UrlItem is null / investigationStatus is invalid / Url is Inactive, Otherwise true
     */
    private boolean isUrlItemWithInvalidStatusOrInActive(AmazonPayDomainValidationItem amazonPayDomainValidationItem) {
        return Objects.isNull(amazonPayDomainValidationItem)
                || StringUtils.isEmpty(amazonPayDomainValidationItem.getInvestigationStatus())
                || Boolean.FALSE.equals(amazonPayDomainValidationItem.getIsActive());
    }

    /**
     * This method will update InvestigationStatus In DomainValidation DDB.
     */
    private void updateInvestigationStatusInDomainValidationDDB(final String clientReferenceGroupId,
                                                                final String url,
                                                                final String status) {
        domainValidationDDBAdapter.createOrUpdateEntry(AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .investigationStatus(status)
                .caseCreationTime(Instant.now().toEpochMilli())
                .build());
    }

    /**
     * This method starts new execution of StepFunction.
     */
    private void startNewWorkFlow(final ProcessInvestigationRequest processInvestigationRequest) {
        final String stepFunctionArn = urlReviewWorkflowMap.get(AMAZON_PAY_BUSINESS);
        try {
            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[STARTED_NEW_WORKFLOW] Starting new execution of StepFunction for Appeal flow with "
                            + "ClientRefGrpId: {} and url: {}.",
                    processInvestigationRequest.getClientReferenceGroupId(), processInvestigationRequest.getUrl());

            // Setting InvestigationType for Merchant Appeal flow.
            processInvestigationRequest.setInvestigationType(InvestigationType.APPEAL.getInvestigationType());

            // Currently hard-coding to AmazonPay business, change it to fetch from request when we have it there.
            stepFunctionAdapter.startWorkflow(stepFunctionArn,
                    objectMapper.writeValueAsString(processInvestigationRequest));
        } catch (JsonProcessingException e) {
            final String msg = "Exception occur while starting the workflow for clientRefGroupId: "
                    + processInvestigationRequest.getClientReferenceGroupId()
                    + " url: " + processInvestigationRequest.getUrl();
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        }
    }

    /**
     * This method sends Response back to lambda.
     */
    private void sendLambdaResponse(final int responseStatusCode, final OutputStream outputStream,
                                    final String lambdaName, final String responseDetails) {
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(responseStatusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(responseDetails)
                .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                .mapper(objectMapper)
                .build());
    }

    /**
     * This method resume the workflow from step function.
     *
     * @param subInvestigationTaskToken taskToken to resume the workflow
     * @param inputString input for the step function.
     * @return int statusCode.
     */
    private int resumeWorkflow(final String subInvestigationTaskToken, final String inputString) {

        boolean status;
        status = stepFunctionAdapter.resumeWorkflow(subInvestigationTaskToken, inputString);
        if (!status) {
            log.info("step function response: {} is received", status);
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(EXECUTE_MANUAL_RESPONSE_WORKFLOW_INVALID_TASK_TOKEN);
            return FAILED_STATUS_CODE;
        }
        /*
        Note : MetricFilter syntax pattern should be updated in CDK package accordingly
        if there is a change in Log message
        */
        log.info("[RESUME_WORKFLOW] Resumed Step function successfully for payload {}", inputString);
        return SUCCESS_STATUS_CODE;
    }

    private boolean isWorkflowFailed(final String workflowId) {
        final String workflowStatus = stepFunctionAdapter.getWorkflowStatus(workflowId);
        log.info("Workflow Status for workflowId: {} is: {}", workflowId, workflowStatus);
        final List<String> invalidStatusList = List.of(ExecutionStatus.FAILED.toString(),
                ExecutionStatus.ABORTED.toString(), ExecutionStatus.TIMED_OUT.toString());
        return invalidStatusList.contains(workflowStatus);
    }

    private String getLowerCaseUrl(final String url) {
        try {
            return URLCaseSensitivityConvertorUtil.getLowerCaseConvertedURL(url);
        } catch (MalformedURLException e) {
            log.error("Exception encountered while converting the url to lowercase for url: {}", url, e);
            final String msg = "Exception encountered while converting the url to lowercase for "
                    + ", url: " + url;
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        }
    }
}
