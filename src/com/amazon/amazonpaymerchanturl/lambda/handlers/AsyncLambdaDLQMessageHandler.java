package com.amazon.amazonpaymerchanturl.lambda.handlers;

import static com.amazon.amazonpaymerchanturl.constants.DeadLetterQueue.RETRY_LOWER_LIMIT;
import static com.amazon.amazonpaymerchanturl.constants.DeadLetterQueue.RETRY_UPPER_LIMIT;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.DLQ;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_EVENT_PARSING_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_NOT_CLASSIFIED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_COUNT_INCREMENT_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_LOWER_LIMIT_CROSSED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_UPPER_LIMIT_CROSSED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.UNDERSCORE;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_URL_REVIEW_WORKFLOW;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.SQS;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.ProcessInvestigationRequest;
import com.amazon.amazonpaymerchanturl.model.QueueEvent;
import com.amazon.amazonpaymerchanturl.model.SQSRecord;
import com.amazon.amazonpaymerchanturl.model.TriggerURLReviewRequest;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ========================================================
 * Common Handler for DLQ Pollers of Async Lambda Functions.
 * ========================================================
 * <p>
 * a. Gets the event records from input stream
 * <p>
 * b. Parses the sqs records
 * <p>
 * c. Modifies each record depending on the origin lambda and request type in the respective DLQ.
 * <p>
 * d. Validates the retry count. Poller frequency will be greater than 15 mins. Hence, the
 * message will be retried for min 2 days - RETRY_UPPER_LIMIT : 192 (sev2), RETRY_LOWER_LIMIT : 24 (sev3)
 * <p>
 * e. Increments the retry count post validation, if valid.
 * <p>
 * f. Deletes the records from DLQ post processing - if valid or invalid(discards).
 * <p>
 * g. Sends response to output stream.
 */
@RequiredArgsConstructor
@Log4j2
public class AsyncLambdaDLQMessageHandler implements RequestStreamHandler {

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final AWSLambda lambdaClient;
    private final AmazonSQS sqsClient;
    private final LambdaResponseUtil lambdaResponseUtil;

    public AsyncLambdaDLQMessageHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.lambdaClient = lambdaComponent.providesLambdaServiceClient();
        this.sqsClient = lambdaComponent.providesSQSServiceClient();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
    }

    /**
     * Handles the requests from async lambda DLQ for re-processing.
     *
     * @param inputStream  InputStream
     * @param outputStream OutputStream
     * @param context      Context
     */
    public void handleRequest(@NonNull final InputStream inputStream, @NonNull final OutputStream outputStream,
                              @NonNull final Context context) {

        final String originLambdaName = Lambda.pollerToOriginFunctionName(context);
        log.info("DLQ poller lambda invoked for origin lambda : {}", originLambdaName);

        final List<SQSRecord> eventRecords = getEventRecords(inputStream, originLambdaName);
        log.info("Found {} messages for this execution", eventRecords.size());

        if (CollectionUtils.isNotEmpty(eventRecords)) {
            eventRecords.parallelStream().forEach(sqsRecord -> {
                log.debug("Invoking origin lambda with input: {}", sqsRecord.getBody());

                final String modifiedRecordBody = modifyRecord(sqsRecord, originLambdaName).getBody();

                if (StringUtils.isNotEmpty(modifiedRecordBody)) {
                    Lambda.invokeLambdaAsync(
                            Lambda.InvokeLambdaAsyncInput.builder()
                                    .lambdaClient(lambdaClient)
                                    .sqsClient(sqsClient)
                                    .lambdaName(originLambdaName)
                                    .payload(modifiedRecordBody)
                                    .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                                    .context(context)
                                    .build()
                    );
                }
                SQS.deleteMsgFromLambdaDLQ(
                        SQS.DeleteMsgFromLambdaDLQInput.builder()
                                .sqsClient(this.sqsClient)
                                .lambdaName(originLambdaName)
                                .receiptHandle(sqsRecord.getReceiptHandle())
                                .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                                .context(context).build()
                );
            });

            log.info("Successfully processed DLQ messages with status code : {}", SUCCESS_STATUS_CODE);
            lambdaResponseUtil.sendResponse(
                    LambdaResponseInput.builder()
                            .statusCode(SUCCESS_STATUS_CODE)
                            .outputStream(outputStream)
                            .lambdaName(originLambdaName)
                            .details(StringUtils.EMPTY)
                            .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                            .mapper(objectMapper)
                            .build()
            );
        }
    }

    private List<SQSRecord> getEventRecords(final InputStream inputStream, final String originLambdaName) {
        List<SQSRecord> sqsRecords = new ArrayList<>();
        QueueEvent event = null;
        try {
            event = objectMapper.readValue(inputStream, QueueEvent.class);
        } catch (final IOException e) {
            log.info("Unable to parse SQS event");
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_EVENT_PARSING_FAILED + UNDERSCORE + originLambdaName + DLQ);
        }
        if (Objects.nonNull(event)) {
            sqsRecords = event.getRecords();
        }
        return sqsRecords;
    }

    private SQSRecord modifyRecord(SQSRecord sqsRecord, final String originLambdaName) {
        log.debug("Message handle : {} is received with delay {} mins",
                sqsRecord.getReceiptHandle(), TimeUnit.MILLISECONDS.toMinutes(
                        Instant.now().toEpochMilli() - Long.parseLong(sqsRecord.getAttributes().getSentTimestamp()))
        );
        sqsRecord.setBody(incrementRetryCount(sqsRecord.getBody(), originLambdaName));
        return sqsRecord;
    }

    private String incrementRetryCount(final String jsonRecord, final String originLambdaName) {
        try {
            switch (originLambdaName) {
                case EXECUTE_URL_REVIEW_WORKFLOW:
                    return incrementTriggerURLReviewRequestRetryCount(jsonRecord, originLambdaName);
                case EXECUTE_MANUAL_RESPONSE_WORKFLOW:
                    return incrementProcessInvestigationRequestRetryCount(jsonRecord, originLambdaName);
                default:
                    log.info("Lambda: {} is not classified", originLambdaName);
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                            SQS_MSG_NOT_CLASSIFIED + UNDERSCORE + DLQ);
                    return StringUtils.EMPTY;
            }
        } catch (final IOException e) {
            log.info("Unable to process message. Discarding record: {} from dead letter queue.", jsonRecord);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_MSG_RETRY_COUNT_INCREMENT_FAILED + UNDERSCORE + originLambdaName + DLQ);
            return StringUtils.EMPTY;
        }
    }

    private String incrementTriggerURLReviewRequestRetryCount(final String jsonRecord,
                                                              final String originLambdaName) throws IOException {
        TriggerURLReviewRequest triggerURLReviewRequest = objectMapper.readValue(
                jsonRecord, TriggerURLReviewRequest.class);

        long retryCount = triggerURLReviewRequest.getRetryCount();

        if (isValidRetryCount(retryCount, jsonRecord, originLambdaName)) {
            triggerURLReviewRequest.setRetryCount(retryCount + 1);
            return objectMapper.writeValueAsString(triggerURLReviewRequest);
        }
        return StringUtils.EMPTY;
    }

    private String incrementProcessInvestigationRequestRetryCount(final String jsonRecord,
                                                                  final String originLambdaName) throws IOException {
        ProcessInvestigationRequest processInvestigationRequest =
                objectMapper.readValue(jsonRecord, ProcessInvestigationRequest.class);

        long retryCount = processInvestigationRequest.getRetryCount();

        if (isValidRetryCount(retryCount, jsonRecord, originLambdaName)) {
            processInvestigationRequest.setRetryCount(retryCount + 1);
            return objectMapper.writeValueAsString(processInvestigationRequest);
        }
        return StringUtils.EMPTY;
    }

    private boolean isValidRetryCount(final long retryCount, final String jsonRecord,
                                      final String originLambdaName) {
        if (retryCount > RETRY_UPPER_LIMIT) {
            log.info("All the retry attempts have exhausted. Discarding record: {} from dead letter queue ",
                    jsonRecord);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_MSG_RETRY_UPPER_LIMIT_CROSSED + UNDERSCORE + originLambdaName + DLQ);
            return false;
        } else if (retryCount > RETRY_LOWER_LIMIT) {
            log.info("Lower retry limit breached for the record: {}", jsonRecord);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_MSG_RETRY_LOWER_LIMIT_CROSSED + UNDERSCORE + originLambdaName + DLQ);
        }
        return true;
    }
}
