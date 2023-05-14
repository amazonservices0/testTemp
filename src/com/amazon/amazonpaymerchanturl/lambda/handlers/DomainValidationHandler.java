package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaymerchanturl.adapter.ParagonInvestigationServiceAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StegoServiceAdapter;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.SQS;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.constants.InvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.WeblabEverCTreatmentMapper;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.Attributes;
import com.amazon.amazonpaymerchanturl.model.ManualUrlReview;
import com.amazon.amazonpaymerchanturl.model.QueueEvent;
import com.amazon.amazonpaymerchanturl.model.SQSRecord;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.TriggerURLReviewRequest;
import com.amazon.amazonpaymerchanturl.model.ProcessInvestigationRequest;
import com.amazon.amazonpaymerchanturl.model.WebsiteReviewRequest;
import com.amazon.amazonpaymerchanturl.model.WebsiteReviewResponse;

import com.amazon.amazonpaymerchanturl.processor.URLValidationResultProcessor;
import com.amazon.amazonpaymerchanturl.provider.WeblabTreatmentInformationProvider;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.amazonpaymerchanturl.utils.StegoDBUrlUpdateUtil;
import com.amazon.amazonpaymerchanturl.utils.ExceptionHandlers;
import com.amazon.amazonpaymerchanturl.utils.UrlStatusNotificationUtil;
import com.amazon.apurlvalidation.UrlValidation;
import com.amazon.urlvalidationdaggerclientconfig.component.DaggerURLValidationComponent;
import com.amazon.apurlvalidation.model.UrlResponse;
import com.amazon.paragoninvestigationservice.Reason;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.cloudcover.agent.CloudCoverJavaAgent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Builder;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.pollerToOriginFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.DeadLetterQueue.RETRY_UPPER_LIMIT;
import static com.amazon.amazonpaymerchanturl.constants.DeadLetterQueue.RETRY_LOWER_LIMIT;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationType.PERIODIC_INVESTIGATION;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.DDB_ENTRY_NOT_FOUND;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PROCESS_INVESTIGATION_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PROCESS_INVESTIGATION_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_EVENT_NULL_RECORDS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_EVENT_PARSING_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_NOT_CLASSIFIED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_COUNT_INCREMENT_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_LOWER_LIMIT_CROSSED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_UPPER_LIMIT_CROSSED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.TRIGGER_URL_REVIEW_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.TRIGGER_URL_REVIEW_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_URL_REVIEW_WORKFLOW;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.PROCESS_INVESTIGATION_RESPONSE_LAMBDA;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.TRIGGER_URL_REVIEW_LAMBDA;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getLWAClientId;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getMerchantId;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getMarketplaceId;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getInvestigationRegionIdentifier;
import static com.amazon.amazonpaymerchanturl.utils.ExceptionHandlers.defaultHandler;
import static com.amazon.amazonpaymerchanturl.utils.URLCaseSensitivityConvertorUtil.getLowerCaseConvertedURL;
import static java.lang.Long.parseLong;

@Builder
@RequiredArgsConstructor
@Log4j2
public class DomainValidationHandler {

    private static final String CONTEXT_TYPE = "requesterInfo";
    private static final String STATUS_CODE_NAME = "statusCode";
    private static final String BODY_NAME = "body";
    private static final Long TENANT_ID = 178L;
    private static final String HTTPS_PROTOCOL = "https";
    private static final String DELIMITER = "://";
    private static final String CLIENT_REFERENCE_GROUP_ID = "clientReferenceGroupId";
    private static final String URL = "url";
    private static final String REVIEW_STATUS = "Review_Status";
    private static final String REDIRECT_URL = "Redirect_URL";
    private static final String AMAZON_PAY_BUSINESS = "AmazonPay";

    private final LambdaComponent lambdaComponent;
    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final ParagonInvestigationServiceAdapter paragonInvestigationServiceAdapter;
    private final ObjectMapper mapper;
    private final StegoServiceAdapter stegoServiceAdapter;
    private final AWSLambda lambdaClient;
    private final AmazonSQS sqsClient;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final Map<String, String> queueIdMap;
    private final String urlStatusNotificationTopic;
    private final StegoDBUrlUpdateUtil stegoDBUrlUpdateUtil;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final UrlValidation urlValidation;
    private final URLValidationResultProcessor urlValidationResultProcessor;
    private final UrlStatusNotificationUtil urlStatusNotificationUtil;
    private final CloudCoverJavaAgent cloudCoverJavaAgent;
    private final WeblabTreatmentInformationProvider weblabProvider;

    public DomainValidationHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.queueIdMap = lambdaComponent.providesQueueIDMap();
        this.mapper = lambdaComponent.providesObjectMapper();
        this.domainValidationDDBAdapter = lambdaComponent.providesDomainValidationDDBAdapter();
        this.stegoServiceAdapter = lambdaComponent.providesStegoServiceAdapter();
        this.paragonInvestigationServiceAdapter = lambdaComponent.getParagonInvestigationServiceAdapter();
        this.lambdaClient = lambdaComponent.providesLambdaServiceClient();
        this.sqsClient = lambdaComponent.providesSQSServiceClient();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.urlStatusNotificationTopic = lambdaComponent.providesUrlStatusNotificationTopic();
        this.stegoDBUrlUpdateUtil = lambdaComponent.provideStegoDBUrlUpdateUtil();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.urlValidation = DaggerURLValidationComponent.create().getUrlValidation();
        this.urlValidationResultProcessor = lambdaComponent.provideURLValidationResultProcessor();
        this.urlStatusNotificationUtil = lambdaComponent.providesUrlStatusNotificationUtil();
        this.cloudCoverJavaAgent = lambdaComponent.provideCloudCoverJavaAgent();
        this.weblabProvider = lambdaComponent.provideWeblabTreatmentInformationProvider();
    }

    /**
     * triggerURLReview will queue an investigation in paragon.
     * This will also add entry to DDB.
     * Note - all the input validations are handled in API-Gateway
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    //TODO: change name to something like - triggerURLsReview
    public void triggerURLReview(InputStream inputStream, OutputStream outputStream, Context context) {
        String functionName = lambdaFunctionName(context);
        String inputJson;
        TriggerURLReviewRequest triggerURLReviewRequest;
        try {
            triggerURLReviewRequest = mapper.readValue(inputStream, TriggerURLReviewRequest.class);
            inputJson = mapper.writeValueAsString(triggerURLReviewRequest);
        } catch (Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(TRIGGER_URL_REVIEW_FAILURE_METRICS);
            defaultHandler(ExceptionHandlers.DefaultHandlerInput.builder()
                    .exception(e)
                    .lambdaName(functionName)
                    .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                    .context(context).build());
            sendLambdaResponse(FAILED_STATUS_CODE, outputStream, functionName, "input parsing failed");
            cloudCoverJavaAgent.capture();
            return;
        }

        log.info(functionName + " is called with input: {}", inputJson);

        /*
        triggerUrlReviewRequest Urls will be filtered and sent to executeUrlReviewWorkFlow For EverC.
        Unfiltered Urls will be attached to this request and sent back for Manual Investigation (without EverC)
         */
        triggerURLReviewRequest = initiateUrlReviewWorkflowProcess(triggerURLReviewRequest, functionName, context);

        final Map<String, List<String>> reviewURLMetaData = triggerURLReviewRequest.getReviewURLsMetaData();
        if (Objects.isNull(reviewURLMetaData) || CollectionUtils.isNullOrEmpty(reviewURLMetaData.entrySet())) {
            sendLambdaResponse(SUCCESS_STATUS_CODE, outputStream, functionName,
                    "info. required for review is empty(null)");
            cloudCoverJavaAgent.capture();
            return;
        }

        try {
            //update inputJson string with new triggerUrlRequest
            inputJson = mapper.writeValueAsString(triggerURLReviewRequest);
        } catch (JsonProcessingException e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(TRIGGER_URL_REVIEW_FAILURE_METRICS);
            defaultHandler(ExceptionHandlers.DefaultHandlerInput.builder()
                    .exception(e)
                    .lambdaName(functionName)
                    .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                    .context(context).build());
            sendLambdaResponse(FAILED_STATUS_CODE, outputStream, functionName,
                    "Input parsing failed for manual urlReview without EverC");
            cloudCoverJavaAgent.capture();
            return;
        }

        log.info(" TriggerUrlRequest for Manual Investigation without EverC: {}", inputJson);

        boolean inputLoggedInDLQ = false;
        int responseStatusCode = SUCCESS_STATUS_CODE;
        Map<String, Integer> urlToStatus = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : reviewURLMetaData.entrySet()) {
            String urlType = entry.getKey();
            List<String> urls = entry.getValue();

            if (CollectionUtils.isNullOrEmpty(urls)) {
                log.info("For URL type: {}. Provided URL list is null", urlType);
                continue;
            }

            /*TODO: Added hardcode string AmazonPay, will be removed when source of triggerUrlReviewRequest change.
               This is a short term fix till taluva 1B.*/
            final List<UrlResponse> urlStatusResponseList = urlValidation.validateUrls(urls, AMAZON_PAY_BUSINESS);
            ManualUrlReview manualUrlReview = urlValidationResultProcessor.process(urlStatusResponseList,
                    triggerURLReviewRequest, urlType);
            for (String url : manualUrlReview.getUrlList()) {
                try {
                    url = getLowerCaseConvertedURL(url);
                    log.info("Lowercase convert Url for manual Investigation {}", url);

                    initiateURLInvestigation(InitiateURLInvestigationInput
                            .builder()
                            .clientCustomInfo(triggerURLReviewRequest.getClientCustomInformation())
                            .clientReferenceGroupId(triggerURLReviewRequest.getClientReferenceGroupId())
                            .investigationType(triggerURLReviewRequest.getInvestigationType())
                            .url(url)
                            .source(triggerURLReviewRequest.getSource())
                            .urlType(urlType)
                            .build());
                    urlToStatus.put(url, SUCCESS_STATUS_CODE);
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(TRIGGER_URL_REVIEW_SUCCESS_METRICS);
                } catch (Exception e) {
                    urlToStatus.put(url, FAILED_STATUS_CODE);
                    responseStatusCode = FAILED_STATUS_CODE;
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(TRIGGER_URL_REVIEW_FAILURE_METRICS);
                    defaultHandler(ExceptionHandlers.DefaultHandlerInput.builder()
                            .exception(e)
                            .sqsClient(inputLoggedInDLQ ? null : this.sqsClient)
                            .lambdaName(functionName)
                            .jsonInput(inputJson)
                            .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                            .context(context).build());
                    /*
                     * Note - We are not doing any filtering and sending complete input state so no need to send
                     * after first failure.
                     * */
                    inputLoggedInDLQ = true;
                }
            }
        }
        sendLambdaResponse(responseStatusCode, outputStream, functionName, urlToStatus.toString());
        cloudCoverJavaAgent.capture();
    }

    /**
     * processInvestigationResponse updates investigation status in the DDB and Stego Services.
     *
     * @param inputStream  input to the lambda handler.
     * @param outputStream output from handler.
     * @param context      Lambda's context
     */
    public void processInvestigationResponse(InputStream inputStream, OutputStream outputStream, Context context) {
        String lambdaName = lambdaFunctionName(context);
        log.debug(lambdaName + " started");

        String inputJson;
        ProcessInvestigationRequest processInvestigationRequest;

        try {
            processInvestigationRequest = mapper.readValue(inputStream, ProcessInvestigationRequest.class);
            inputJson = mapper.writeValueAsString(processInvestigationRequest);
        } catch (Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_INVESTIGATION_FAILURE_METRICS);
            defaultHandler(ExceptionHandlers.DefaultHandlerInput.builder()
                    .exception(e)
                    .sqsClient(this.sqsClient)
                    .lambdaName(lambdaName)
                    .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                    .context(context).build());
            sendLambdaResponse(FAILED_STATUS_CODE, outputStream, lambdaName, "input parsing failed");
            cloudCoverJavaAgent.capture();
            return;
        }

        log.info("{} is called with input: {}", lambdaName, inputJson);
        int responseStatusCode;
        try {
            responseStatusCode = initiateProcessInvestigationReponse(processInvestigationRequest, lambdaName,
                                                                    inputJson, context);
        } catch (Exception e) {
            defaultHandler(ExceptionHandlers.DefaultHandlerInput.builder()
                    .exception(e)
                    .sqsClient(this.sqsClient)
                    .lambdaName(lambdaName)
                    .jsonInput(inputJson)
                    .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                    .context(context).build());
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_INVESTIGATION_FAILURE_METRICS);
            responseStatusCode = FAILED_STATUS_CODE;
        }
        String responseDetails = String.format("input details - ClientReferenceGroupId %s and Url %s "
                        + "Investigation status %s", processInvestigationRequest.getClientReferenceGroupId(),
                processInvestigationRequest.getUrl(), processInvestigationRequest.getInvestigationStatus());
        sendLambdaResponse(responseStatusCode, outputStream, lambdaName, responseDetails);
        cloudCoverJavaAgent.capture();
    }

    private int initiateProcessInvestigationReponse(final ProcessInvestigationRequest processInvestigationRequest,
                                                    final String lambdaName, final String inputJson,
                                                    final Context context) throws Exception {

        int responseStatusCode = SUCCESS_STATUS_CODE;
        final String url = getLowerCaseConvertedURL(processInvestigationRequest.getUrl());
        AmazonPayDomainValidationItem amazonPayDomainValidationItem = domainValidationDDBAdapter
                .loadEntry(processInvestigationRequest.getClientReferenceGroupId(), url);
        if (Objects.nonNull(amazonPayDomainValidationItem)) {
            log.info("For ClientReferenceGroupId: {} and URL: {}, InvestigationStatus: {} , " +
                            "InvestigationId: {} is received from DDB",
                    processInvestigationRequest.getClientReferenceGroupId(), processInvestigationRequest.getUrl(),
                    amazonPayDomainValidationItem.getInvestigationStatus(),
                    amazonPayDomainValidationItem.getInvestigationId());

            if (StringUtils.isNotEmpty(amazonPayDomainValidationItem.getInvestigationId())) {
                //Invoke executeManualResponseWorkflow if InvestigationID(InvId) is present in DDB
                processInvestigationResponseWithWorkflow(lambdaName, inputJson, context);
                log.info("Request sent successfully to ExecuteManualResponseWorkflow"
                                + " details - ClientReferenceGroupId {} and Url {} Investigation status {}",
                        processInvestigationRequest.getClientReferenceGroupId(),
                        processInvestigationRequest.getUrl(), processInvestigationRequest.getInvestigationStatus());
            } else {
                processInvestigationResponseWithoutWorkflow(amazonPayDomainValidationItem,
                        processInvestigationRequest, url, lambdaName);
            }
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_INVESTIGATION_SUCCESS_METRICS);
        } else {
            responseStatusCode = FAILED_STATUS_CODE;
            log.info("Invalid input: ClientReferenceGroupId: {} and URL: {}, No data found in DDB",
                    processInvestigationRequest.getClientReferenceGroupId(), processInvestigationRequest.getUrl());
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(DDB_ENTRY_NOT_FOUND);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_INVESTIGATION_FAILURE_METRICS);
        }
        return responseStatusCode;
    }

    private void processInvestigationResponseWithoutWorkflow(
            final AmazonPayDomainValidationItem amazonPayDomainValidationItem,
            final ProcessInvestigationRequest processInvestigationRequest, final String url, final String lambdaName) {
        /**
         * Since few merchant might delete store which will cause failure while calling Stego,
         * hence capturing the result of investigation first.
         */
        updateDDBEntry(amazonPayDomainValidationItem, processInvestigationRequest);
        /**
         * In case new flow is enabled, we simply send the url to the SNS topic
         * We do not perform any other operation in this case.
         */
        urlStatusNotificationUtil.buildAndPublishURLReviewNotification(
                amazonPayDomainValidationItem.getClientReferenceGroupId(),
                processInvestigationRequest.getInvestigationStatus(), url,
                amazonPayDomainValidationItem.getCaseCompletionTime(),
                processInvestigationRequest.getReviewInfo(), urlStatusNotificationTopic);

        /**
         * We will not invoke stego in case the url is of redirect type
         * TODO: Remove this call to Stego after complete dialup
         */
        if (!REDIRECT_URL.equals(amazonPayDomainValidationItem.getUrlType())) {
            stegoDBUrlUpdateUtil.getAndUpdateStegoServiceApplication(
                    amazonPayDomainValidationItem.getClientInfo(), amazonPayDomainValidationItem.getUrl(),
                    processInvestigationRequest.getInvestigationStatus(),
                    amazonPayDomainValidationItem.getUrlType());
            log.debug("Form Lambda: {} Stego Service Call (to update complaint status) completed",
                    lambdaName);
        }
    }

    private void processInvestigationResponseWithWorkflow(final String lambdaName, final String inputJson,
                                                          final Context context) {
        String originFunction = lambdaName.replace(PROCESS_INVESTIGATION_RESPONSE_LAMBDA,
                EXECUTE_MANUAL_RESPONSE_WORKFLOW);
        invokeAsyncLambdaHandler(inputJson, originFunction, context);
    }

    /**
     * This method sends Resposne back to lambda.
     */
    private void sendLambdaResponse(final int responseStatusCode,
                                    final OutputStream outputStream,
                                    final String lambdaName,
                                    final String responseDetails) {
        lambdaResponseUtil.sendResponse(LambdaResponseInput.builder()
                .statusCode(responseStatusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .details(responseDetails)
                .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                .mapper(this.mapper)
                .build());
    }

    /**
     * Handler function for processing messages from the dead letter SQS queue attached to the triggerURLReview
     * Lambda function.
     *
     * @param inputStream  input for the poller.
     * @param outputStream output of the handler.
     * @param context      Lambda's context
     */
    public void triggerURLReviewDLQPoller(InputStream inputStream, OutputStream outputStream,
            Context context) {
        processMessagesAndInvokeLambda(inputStream, outputStream, context);
    }

    /**
     * Handler function for processing messages from the dead letter SQS queue attached to the
     * processInvestigationResponse Lambda function.
     *
     * @param inputStream  input for the poller.
     * @param outputStream output of the handler.
     * @param context      Lambda's context
     */
    public void processInvestigationResponseDLQPoller(InputStream inputStream, OutputStream outputStream,
            Context context) {
        processMessagesAndInvokeLambda(inputStream, outputStream, context);
    }

    private void processMessagesAndInvokeLambda(InputStream inputStream, OutputStream outputStream, Context context) {
        String originLambdaName = pollerToOriginFunctionName(context);
        List<SQSRecord> messages = filterMessages(inputStream, context);

        log.info("found {} messages for this run", messages.size());
        for (SQSRecord sqsRecord : messages) {
            log.debug("invoking: {} Lambda with input: {}", originLambdaName, sqsRecord.getBody());
            //TODO: add batching (based on time-out limit batch size could be decided)
            Lambda.invokeLambdaAsync(Lambda.InvokeLambdaAsyncInput.builder()
                    .lambdaClient(this.lambdaClient)
                    .sqsClient(this.sqsClient)
                    .lambdaName(originLambdaName)
                    .payload(sqsRecord.getBody())
                    .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                    .context(context).build());
        }
        sendLambdaResponse(SUCCESS_STATUS_CODE, outputStream, originLambdaName, StringUtils.EMPTY);
    }

    /*
     * Filters SQS messages(input) for which execution(Lambda invocation) should be retried
     * */
    private List<SQSRecord> filterMessages(InputStream inputStream, Context context) {

        String originLambdaName = pollerToOriginFunctionName(context);
        QueueEvent event;
        List<SQSRecord> messages = new ArrayList<>();

        try {
            event = mapper.readValue(inputStream, QueueEvent.class);
        } catch (Exception e) {
            this.cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(SQS_EVENT_PARSING_FAILED);
            log.error("unable to parse SQS event");
            return messages;
        }

        if (event.getRecords() == null) {
            this.cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(SQS_EVENT_NULL_RECORDS);
            log.error("Lambda is triggered for null set of records");
            return messages;
        }

        for (SQSRecord sqsRecord : event.getRecords()) {
            Attributes attributes = sqsRecord.getAttributes();
            long sentTime = parseLong(attributes.getSentTimestamp());
            long currentTime = Instant.now().toEpochMilli();
            long timeDelta = TimeUnit.MILLISECONDS.toMinutes(currentTime - sentTime);

            log.debug("message handle : {} is received with delay {} min",
                    sqsRecord.getReceiptHandle(), timeDelta);

            String msgJson = incrementAndValidateRetryCount(sqsRecord.getBody(), originLambdaName);
            if (!msgJson.isEmpty()) {
                sqsRecord.setBody(msgJson);
                messages.add(sqsRecord);
            }
            SQS.deleteMsgFromLambdaDLQ(SQS.DeleteMsgFromLambdaDLQInput.builder()
                    .sqsClient(this.sqsClient)
                    .lambdaName(originLambdaName)
                    .receiptHandle(sqsRecord.getReceiptHandle())
                    .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                    .context(context).build());
        }
        return messages;
    }

    /*
     * increments the retry count and validates if it is within the threshold.
     * Returns empty string ("") in case of validation or input parsing failure.
     * */
    private String incrementAndValidateRetryCount(String msgJson, String originLambdaName) {
        long retryCnt = 0;
        try {
            if (originLambdaName.contains(TRIGGER_URL_REVIEW_LAMBDA)) {
                TriggerURLReviewRequest triggerURLReviewRequest = mapper.readValue(msgJson,
                        TriggerURLReviewRequest.class);
                retryCnt = triggerURLReviewRequest.getRetryCount() == null ? 0
                        : triggerURLReviewRequest.getRetryCount();
                triggerURLReviewRequest.setRetryCount(retryCnt + 1);
                msgJson = mapper.writeValueAsString(triggerURLReviewRequest);
            } else if (originLambdaName.contains(PROCESS_INVESTIGATION_RESPONSE_LAMBDA)) {
                ProcessInvestigationRequest processInvestigationRequest =
                        mapper.readValue(msgJson, ProcessInvestigationRequest.class);
                retryCnt = processInvestigationRequest.getRetryCount() == null ? 0
                        : processInvestigationRequest.getRetryCount();
                processInvestigationRequest.setRetryCount(retryCnt + 1);
                msgJson = mapper.writeValueAsString(processInvestigationRequest);
            } else {
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(SQS_MSG_NOT_CLASSIFIED);
                log.error("lambda: {} is not classified", originLambdaName);
                return "";
            }
        } catch (IOException e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(SQS_MSG_RETRY_COUNT_INCREMENT_FAILED);
            log.error("Unable to process message. Discarding from dead letter queue " +
                    "record: {}", msgJson);
            return "";
        }

        //Note - Poller frequency will be greater than 15 min so message will retried for min 2 day.
        if (retryCnt > RETRY_UPPER_LIMIT) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(SQS_MSG_RETRY_UPPER_LIMIT_CROSSED);
            log.error("all the retry attempts are exhausted. Discarding from dead letter queue " +
                    "record: {}", msgJson);
            return "";
        } else if (retryCnt > RETRY_LOWER_LIMIT) {
            log.info("Bleached the lower retry limit for the record: {}", msgJson);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(SQS_MSG_RETRY_LOWER_LIMIT_CROSSED);
        }
        return msgJson;
    }

    private void updateDDBEntry(final AmazonPayDomainValidationItem amazonPayDomainValidationItem,
            final ProcessInvestigationRequest processInvestigationRequest) {
        if (!StringUtils.equals(amazonPayDomainValidationItem.getInvestigationStatus(),
                processInvestigationRequest.getInvestigationStatus())
                || !StringUtils.equals(amazonPayDomainValidationItem.getReviewInfo(),
                processInvestigationRequest.getReviewInfo())) {
            amazonPayDomainValidationItem.setInvestigationStatus(processInvestigationRequest
                    .getInvestigationStatus());
            amazonPayDomainValidationItem.setReviewInfo(processInvestigationRequest.getReviewInfo());
            amazonPayDomainValidationItem.setCaseCompletionTime(Instant.now().toEpochMilli());
            domainValidationDDBAdapter.createOrUpdateEntry(amazonPayDomainValidationItem);
        }
    }

    private void createDDBEntry(@NonNull final String clientReferenceGroupId,
                                @NonNull final String clientCustomInformation,
                                @NonNull final String source,
                                @NonNull final String url, @NonNull final Long caseCreationTime,
                                @NonNull final Long caseId, @NonNull final String urlType) {
        final AmazonPayDomainValidationItem ddbEntry = AmazonPayDomainValidationItem.builder()
                .url(url)
                .clientReferenceGroupId(clientReferenceGroupId)
                .paragonCaseId(caseId)
                .clientInfo(getLWAClientId(clientCustomInformation))
                .urlSource(source)
                .caseCreationTime(caseCreationTime)
                .urlType(urlType)
                .investigationStatus(InvestigationStatus.IN_REVIEW.getInvestigationStatus())
                .build();
        domainValidationDDBAdapter.createOrUpdateEntry(ddbEntry);
    }

    private WebsiteReviewResponse queueInvestigationInParagon(@NonNull final String originURL,
                                                              @NonNull final String clientCustomInformation,
                                                              @NonNull final String clientReferenceGroupId) {
        final String investigationRegionIdentifier = getInvestigationRegionIdentifier(clientCustomInformation);
        final String investigationQueue = queueIdMap.get(investigationRegionIdentifier);

        log.info("Selected investigation Queue: {}", investigationQueue);

        final List<Reason> reason = new ArrayList<>();
        final Map<String, List<String>> context = new HashMap<>();
        context.put(CONTEXT_TYPE, Collections.singletonList(originURL));
        final WebsiteReviewRequest paragonInvestigationRequest = WebsiteReviewRequest
                .builder()
                .merchantId(getMerchantId(clientReferenceGroupId))
                .investigationQueueName(investigationQueue)
                .marketplaceId(getMarketplaceId(clientReferenceGroupId))
                .tenantId(TENANT_ID)
                .reasons(reason)
                .context(context)
                .build();

        return paragonInvestigationServiceAdapter
                .createParagonInvestigation(paragonInvestigationRequest);
    }

    /*
     * TODO: instead of Exception we should throw whatever we are expecting but called method signature is not signaling
     *  so throwing Exception. Once this issue is fixed we can put specific exception
     * */
    private void initiateURLInvestigation(InitiateURLInvestigationInput input) throws Exception {
        AmazonPayDomainValidationItem domainValidationDDBEntry =
                domainValidationDDBAdapter.loadEntry(input.clientReferenceGroupId, input.url);
        log.info("DDB entry for clientReferenceGroupId {} and Url {} is {}",
                input.clientReferenceGroupId, input.url, domainValidationDDBEntry);
        String investigationStatus;
        /**
         * Following cases can happen:
         * 1. If entry in DDB is present, then we send notification to SNS and update Stego (as per previous flow)
         * 2. If entry in DDB is not present, then we start ivestigation in Paragon and send notification to SNS
         *    using investigationStatus as IN_Review
         *
         * Note: The updateStegoIfApplicable method will be removed as part of Taluva 1B and the remaining logic
         * will remain as is.
         */
        boolean redundantInvestigationCall = Objects.nonNull(domainValidationDDBEntry)
                && (!input.investigationType.equals(PERIODIC_INVESTIGATION.getInvestigationType()));
        if (redundantInvestigationCall) {
            investigationStatus = domainValidationDDBEntry.getInvestigationStatus();

            /**
             * A short term fix. It is seen that many merchant delete their store and add
             * url from a new store. We persist old store id in DDB and hence call to stego using same store.
             * Stego call will fail as store no longer exist. This issue will be solved in subsequent phase.
             * For now we will be persisting the latest store.
             * TODO: Remove this fix, after Phase 1B launch.
             */
            domainValidationDDBEntry.setClientInfo(getLWAClientId(input.clientCustomInfo));
            domainValidationDDBAdapter.createOrUpdateEntry(domainValidationDDBEntry);
            urlStatusNotificationUtil.buildAndPublishURLReviewNotification(
                    domainValidationDDBEntry.getClientReferenceGroupId(),
                    investigationStatus, input.url, domainValidationDDBEntry.getCaseCompletionTime(),
                    domainValidationDDBEntry.getReviewInfo(), urlStatusNotificationTopic);

            /**
             * We will not invoke stego in case the url is of redirect type
             * TODO: Remove this call to Stego after complete dialup
             */
            if (!REDIRECT_URL.equals(domainValidationDDBEntry.getUrlType())) {
                updateStegoIfApplicable(domainValidationDDBEntry, input);
            }
        } else {
            investigationStatus = InvestigationStatus.IN_REVIEW.getInvestigationStatus();
            final WebsiteReviewResponse paragonInvestigationResponse = queueInvestigationInParagon(
                    input.url, input.clientCustomInfo, input.clientReferenceGroupId);
            log.info("Origin url: {}. Paragon queue investigation response: {}",
                    input.url, paragonInvestigationResponse);

            Long caseCreationTime = Instant.now().toEpochMilli();
            createDDBEntry(input.clientReferenceGroupId, input.clientCustomInfo, input.source,
                    input.url, caseCreationTime, paragonInvestigationResponse.getInvestigationId(), input.urlType);

            // Review_Info is not present in case of creating a new investigation
            urlStatusNotificationUtil.buildAndPublishURLReviewNotification(input.clientReferenceGroupId,
                    investigationStatus, input.url, caseCreationTime, "", urlStatusNotificationTopic);
        }
    }

    @Builder
    private static final class InitiateURLInvestigationInput {
        private final String url, clientCustomInfo, clientReferenceGroupId, source, urlType, investigationType;
    }
    /**
     * Updae stego service if applicable (using previous logic)
     */
    private void updateStegoIfApplicable(AmazonPayDomainValidationItem domainValidationDDBEntry,
                                         InitiateURLInvestigationInput input) {
        boolean redundantInvestigationCall = domainValidationDDBEntry != null
                && (!input.investigationType.equals(PERIODIC_INVESTIGATION.getInvestigationType()));
        if (redundantInvestigationCall) {
            log.debug("investigation is already triggered for url: {} and this is not periodic investigation"
                            + " so not queuing investigation. DDB entry: {}",
                    input.url, domainValidationDDBEntry);
            /**
             * Check if the Url is compliant and if it is compliant add URL in Stego Service.
             * If a merchant deletes allowed origin url from UI and then again tries to add it,
             * below call can assure that url is updated in stego.
             */
            stegoDBUrlUpdateUtil.getAndUpdateStegoServiceApplication(getLWAClientId(input.clientCustomInfo), input.url,
                    domainValidationDDBEntry.getInvestigationStatus(), domainValidationDDBEntry.getUrlType());
        }
    }

    /**
     * This method invokes {@link ExecuteUrlReviewWorkflowHandler} lambda function when weblab is dialed up
     * The current triggerUrlReview input request will have only one type of urls, but in future we may get
     * multiple url types in single request. So this method will get weblab treatments for each url type and
     * filter the urls that needs to be sent for ExecuteUrlReviewWorkflowHandler
     * @param triggerURLReviewRequest triggerUrlReview input request
     * @param functionName lambda function name
     * @param context context
     * @return triggerUrlRequest for Manual Review without EverC
     */
    private TriggerURLReviewRequest initiateUrlReviewWorkflowProcess(TriggerURLReviewRequest triggerURLReviewRequest,
                                                final String functionName, final Context context) {

        if (MapUtils.isEmpty(triggerURLReviewRequest.getReviewURLsMetaData())) {
            return triggerURLReviewRequest;
        }

        final Map<String, List<String>> reviewURLMetaData = new HashMap<>(
                triggerURLReviewRequest.getReviewURLsMetaData());
        final String urlSource = triggerURLReviewRequest.getSource();

        //filteredReviewUrlMetaData contains Urls that need to be sent for executeUrlReviewWorkflow(EverC)
        Map<String, List<String>> filteredReviewURLMetaData = reviewURLMetaData.entrySet().stream()
                .filter(entry -> (
                        !CollectionUtils.isNullOrEmpty(entry.getValue()) &&
                                isWeblabDialedUpForUrlType(triggerURLReviewRequest, urlSource, entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (MapUtils.isEmpty(filteredReviewURLMetaData)) {
            log.info("All Urls are sent for Manual Investigation without EverC");
            return triggerURLReviewRequest;
        }

        //if any Urls are present for EverC Review , make payload and Invoke ExecuteUrlReviewWorkFlow
        final String payload;
        try {
            payload = getPayloadForTriggerUrlRequest(triggerURLReviewRequest, filteredReviewURLMetaData);
        } catch (JsonProcessingException e) {
            log.error("Error while parsing payload for ExecuteUrlReviewRequest. " +
                    "Hence, Sending all Urls for Manual Review - Exception : {}", e.getMessage());
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(TRIGGER_URL_REVIEW_FAILURE_METRICS);
            return triggerURLReviewRequest;
        }
        String originFunction = functionName.replace(TRIGGER_URL_REVIEW_LAMBDA, EXECUTE_URL_REVIEW_WORKFLOW);

        //Request(filteredUrls) sent for ExecuteUrlReviewWorkFlow
        invokeAsyncLambdaHandler(payload, originFunction, context);

        /*
        Exclude the urls sent to ExecuteUrlReviewWorkFlow from triggerUrlRequest.
        Remaining urls will be sent for Manual Review.
         */
        reviewURLMetaData.entrySet().removeAll(filteredReviewURLMetaData.entrySet());
        triggerURLReviewRequest.setReviewURLsMetaData(reviewURLMetaData);
        return triggerURLReviewRequest;
    }

    /**
     * Returns true if weblab returns expected treatment for the Url Type and Url source
     * @param triggerURLReviewRequest triggerURlReview request
     * @param urlSource Url Source SC, Swipe, AuthUI etc
     * @param urlType Url type Origin, ReDirect etc
     * @return boolean - true or false
     */
    private boolean isWeblabDialedUpForUrlType(final TriggerURLReviewRequest triggerURLReviewRequest,
                                               final String urlSource,
                                               final String urlType) {
        try {
            final WeblabEverCTreatmentMapper weblab = WeblabEverCTreatmentMapper
                    .getWeblabTreatmentMapper(urlSource, urlType);
            final String merchantId = getMerchantId(triggerURLReviewRequest.getClientReferenceGroupId());
            log.info("isWeblabDialedUpForUrlType with urlType : {}, Source : {}, Weblab : {} for MerchantId : {}",
                    urlType, urlSource, weblab, merchantId);
            final String marketPlaceID = getMarketplaceId(triggerURLReviewRequest.getClientReferenceGroupId());
            final String treatment = weblabProvider.getWebLabTreatmentInformation(merchantId,
                    marketPlaceID, weblab.getWeblabName());
            return treatment.equals(weblab.getTreatment());
        } catch (IllegalArgumentException exception) {
            log.info("Weblab not found for given UrlSource : {} and urlType : {}", urlSource, urlType);
            return false;
        }
    }

    /**
     * Method used to invoke Lambda function.
     * @param payload payload for input request
     * @param originFunctionName Name of target lambda function
     * @param context context
     */
    private void invokeAsyncLambdaHandler(final String payload, final String originFunctionName,
                                          final Context context) {
        log.info(" lambda invoked for origin function : {} with payload : {} ", originFunctionName, payload);
        Lambda.invokeLambdaAsync(Lambda.InvokeLambdaAsyncInput.builder()
                .lambdaClient(this.lambdaClient)
                .sqsClient(this.sqsClient)
                .lambdaName(originFunctionName)
                .payload(payload)
                .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                .context(context).build());
    }

    /**
     * Returns Payload as String for TriggerUrlRequest from filtered Url List
     * @param triggerURLReviewRequest triggerURLReviewRequest
     * @param filteredReviewURLMetaData urls that will be sent for review
     * @return String - payload
     * @throws JsonProcessingException is thrown when parsing triggerUrlRequest is failed
     */
    private String getPayloadForTriggerUrlRequest(final TriggerURLReviewRequest triggerURLReviewRequest,
                         final Map<String, List<String>> filteredReviewURLMetaData) throws JsonProcessingException {
        //make a TriggerURLReviewRequest with filtered reviewUrls
        TriggerURLReviewRequest filteredTriggerURLReviewRequest = TriggerURLReviewRequest.builder()
                .reviewURLsMetaData(filteredReviewURLMetaData)
                .clientCustomInformation(triggerURLReviewRequest.getClientCustomInformation())
                .clientReferenceGroupId(triggerURLReviewRequest.getClientReferenceGroupId())
                .investigationType(triggerURLReviewRequest.getInvestigationType())
                .severity(triggerURLReviewRequest.getSeverity())
                .source(triggerURLReviewRequest.getSource())
                .retryCount(triggerURLReviewRequest.getRetryCount())
                .build();
        return mapper.writeValueAsString(filteredTriggerURLReviewRequest);
    }
}
