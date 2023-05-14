package com.amazon.amazonpaymerchanturl.lambda.handlers;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.ErrorQueue.RETRY_LOWER_LIMIT;
import static com.amazon.amazonpaymerchanturl.constants.ErrorQueue.RETRY_UPPER_LIMIT;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_EVENT_PARSING_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_COUNT_INCREMENT_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_LOWER_LIMIT_CROSSED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.SQS_MSG_RETRY_UPPER_LIMIT_CROSSED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.UNDERSCORE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;

import lombok.NonNull;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.SQS;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.QueueEvent;
import com.amazon.amazonpaymerchanturl.model.UrlVendorReviewScanSpecInput;
import org.apache.commons.lang3.StringUtils;

import com.amazon.amazonpaymerchanturl.task.CallbackWorkflowDeterminatorTask;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazon.amazonpaymerchanturl.model.SQSRecord;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ExecuteVendorResponseWorkflowErrorQueuePoller polls messages from error queue
 * and calls callback workflow determinator task to initiate or resume workflow.
 * <p>
 * a. Gets the event records from input stream
 * <p>
 * b. Parses the sqs records
 * <p>
 * c. Modifies each record with retry count.
 * <p>
 * d. Validates the retry count. Poller frequency will be greater than 15 mins. Hence, the
 * message will be retried for min 2 days - RETRY_UPPER_LIMIT : 192 (sev2), RETRY_LOWER_LIMIT : 24 (sev3)
 * <p>
 * e. Increments the retry count post validation, if valid.
 * <p>
 * f. Deletes the records from error queue post processing - if valid or invalid(discards).
 * <p>
 * g. Sends response to output stream.
 */

@RequiredArgsConstructor
@Builder
@Log4j2
public class ExecuteVendorResponseWorkflowErrorQueuePoller implements RequestStreamHandler {
    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final CallbackWorkflowDeterminatorTask callbackWorkflowDeterminatorTask;
    private final AmazonSQS sqsClient;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final String errorQueueUrl;

    public ExecuteVendorResponseWorkflowErrorQueuePoller() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.callbackWorkflowDeterminatorTask = lambdaComponent.providesCallbackWorkflowDeterminatorTask();
        this.sqsClient = lambdaComponent.providesSQSServiceClient();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.errorQueueUrl = lambdaComponent.providesExecuteVendorResponseWorkflowErrorQueueUrl();
    }

    /**
     * handleRequest the requests from lambda error queue for re-processing.
     * @param inputStream       input stream
     * @param outputStream      output stream
     * @param context           context
     */
    public void handleRequest(@NonNull final InputStream inputStream, @NonNull final OutputStream outputStream,
                              @NonNull final Context context) {
        final String lambdaFunctionName = lambdaFunctionName(context);
        log.info(lambdaFunctionName + " lambda invoked.");

        final String errorQueueName = getQueueName(errorQueueUrl);

        final List<SQSRecord> eventRecords = getEventRecords(inputStream, errorQueueName);
        log.info("Found {} messages for this execution", eventRecords.size());

        if (CollectionUtils.isNotEmpty(eventRecords)) {
            eventRecords.parallelStream().forEach(sqsRecord -> {
                log.debug("Invoking origin lambda with input: {}", sqsRecord.getBody());

                final Optional<UrlVendorReviewScanSpecInput> urlVendorReviewScanSpecInput =
                        getUrlVendorReviewScanSpecInputFromRecord(sqsRecord, errorQueueName);

                urlVendorReviewScanSpecInput.ifPresent(
                        callbackWorkflowDeterminatorTask::initateOrResumeURLReviewWorkflow);

                SQS.deleteMsgFromLambdaQueue(
                        SQS.DeleteMsgFromLambdaQueueInput.builder()
                                .sqsClient(this.sqsClient)
                                .queueUrl(this.errorQueueUrl)
                                .receiptHandle(sqsRecord.getReceiptHandle())
                                .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                                .build()
                );
            });

            log.info("Successfully processed error queue messages with status code : {}", SUCCESS_STATUS_CODE);

            lambdaResponseUtil.sendResponse(
                    LambdaResponseInput.builder()
                            .statusCode(SUCCESS_STATUS_CODE)
                            .outputStream(outputStream)
                            .lambdaName(lambdaFunctionName)
                            .details(StringUtils.EMPTY)
                            .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                            .mapper(objectMapper)
                            .build()
            );
        }
    }

    private List<SQSRecord> getEventRecords(final InputStream inputStream, final String errorQueueUrl) {
        List<SQSRecord> sqsRecords = new ArrayList<>();
        QueueEvent event = null;
        try {
            event = objectMapper.readValue(inputStream, QueueEvent.class);
        } catch (final IOException e) {
            log.info("Unable to parse SQS event");
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_EVENT_PARSING_FAILED + UNDERSCORE + errorQueueUrl);
        }
        if (Objects.nonNull(event)) {
            sqsRecords = event.getRecords();
        }
        return sqsRecords;
    }

    private Optional<UrlVendorReviewScanSpecInput> getUrlVendorReviewScanSpecInputFromRecord(
            final SQSRecord sqsRecord, final String errorQueueName) {
        log.debug("Message handle : {} is received with delay {} mins",
                sqsRecord.getReceiptHandle(), TimeUnit.MILLISECONDS.toMinutes(
                        Instant.now().toEpochMilli() - Long.parseLong(sqsRecord.getAttributes().getSentTimestamp()))
        );
        return incrementRetryCount(sqsRecord.getBody(), errorQueueName);
    }

    private Optional<UrlVendorReviewScanSpecInput> incrementRetryCount(final String jsonRecord,
                                                                       final String errorQueueName) {
        try {
            UrlVendorReviewScanSpecInput urlVendorReviewScanSpecInput = objectMapper.readValue(
                    jsonRecord, UrlVendorReviewScanSpecInput.class);
            long retryCount = urlVendorReviewScanSpecInput.getRetryCount();

            if (isValidRetryCount(retryCount, jsonRecord, errorQueueName)) {
                urlVendorReviewScanSpecInput.setRetryCount(retryCount + 1);
                return Optional.ofNullable(urlVendorReviewScanSpecInput);
            }
        } catch (final IOException e) {
            log.info("Unable to process message. Discarding record: {} from error queue.", jsonRecord);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_MSG_RETRY_COUNT_INCREMENT_FAILED + UNDERSCORE + errorQueueName);
        }
        return Optional.empty();
    }

    private boolean isValidRetryCount(final long retryCount, final String jsonRecord, final String errorQueueName) {
        if (retryCount > RETRY_UPPER_LIMIT) {
            log.info("All the retry attempts have exhausted. Discarding record: {} from error queue ",
                    jsonRecord);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_MSG_RETRY_UPPER_LIMIT_CROSSED + UNDERSCORE + errorQueueName);
            return false;
        } else if (retryCount > RETRY_LOWER_LIMIT) {
            log.info("Lower retry limit breached for the record: {}", jsonRecord);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    SQS_MSG_RETRY_LOWER_LIMIT_CROSSED + UNDERSCORE + errorQueueName);
        }
        return true;
    }

    private String getQueueName(final String queueUrl) {
        return queueUrl.substring(queueUrl.lastIndexOf('/') + 1);
    }
}
