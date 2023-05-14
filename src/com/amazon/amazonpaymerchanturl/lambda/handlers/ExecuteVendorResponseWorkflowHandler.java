package com.amazon.amazonpaymerchanturl.lambda.handlers;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_DESERIALIZATION_FAILURE;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.EXECUTE_VENDOR_RESPONSE_VALIDATION_FAILURE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EVERCOMPLIANT_ID;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EXTENDED_PAYLOAD_SIZE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EXTERNAL_PAYLOAD_S3_POINTER_MODEL_CLASS;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.INTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EXTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.SQS_LARGE_PAYLOAD_SIZE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.VENDOR_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLInvalidInputException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.model.UrlVendorReviewScanSpecInput;
import com.amazon.amazonpaymerchanturl.task.CallbackWorkflowDeterminatorTask;
import com.amazon.amazonpaymerchanturl.translator.ITranslator;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewClientException;
import com.amazon.urlvendorreviewlib.factory.VendorCallbackHandlerFactory;
import com.amazon.urlvendorreviewmodel.model.ScanSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;

import com.amazon.amazonpaymerchanturl.adapter.S3Adapter;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.MessageS3Pointer;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.urlvendorreviewlib.component.UrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.component.DaggerUrlVendorReviewLibComponent;

import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * ExecuteVendorResponseWorkflowHandler polls messages from SQS,
 * if flagged with message attribute, fetch message from S3 based on reference pointer in SQS
 * deserialize and translate the messages from vendor specific to vendor agnostic model
 * and checks if we need to resume or initiate URLReviewWorkflow.
 */
@RequiredArgsConstructor
@Builder
@Log4j2
public class ExecuteVendorResponseWorkflowHandler {
    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final SQSAdapter sqsAdapter;
    private final S3Adapter s3Adapter;
    private final CallbackWorkflowDeterminatorTask callbackWorkflowDeterminatorTask;
    private final UrlVendorReviewLibComponent urlVendorReviewLibComponent;
    private final VendorCallbackHandlerFactory vendorCallbackHandlerFactory;
    private final String vendorResponseValidationErrorQueueUrl;
    private final ITranslator<List<String>, SendMessageBatchRequest> sqsBatchRequestTranslator;

    public ExecuteVendorResponseWorkflowHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapperWithDefaultTyping();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.sqsAdapter = lambdaComponent.providesSQSAdapter();
        this.s3Adapter = lambdaComponent.providesS3Adapter();
        this.callbackWorkflowDeterminatorTask = lambdaComponent.providesCallbackWorkflowDeterminatorTask();
        urlVendorReviewLibComponent = DaggerUrlVendorReviewLibComponent.create();
        vendorCallbackHandlerFactory = urlVendorReviewLibComponent.getVendorCallbackHandlerFactory();
        this.vendorResponseValidationErrorQueueUrl = lambdaComponent.providesVendorResponseValidationErrorQueueUrl();
        this.sqsBatchRequestTranslator = lambdaComponent.providesSQSBatchRequestTranslator();
    }

    /**
     * handleRequest will poll messages from SQS
     * if flagged with message attribute, fetch message from S3 based on reference pointer in SQS
     * deserialize and translate the messages from vendor specific to vendor agnostic model
     * Checks if we need to resume or initiate URLReviewWorkflow.
     * @param sqsEvent  Input to the lambda handler.
     * @param context   Lambda's context
     */
    public void handleRequest(@NonNull final SQSEvent sqsEvent, @NonNull final Context context) {
        String lambdaFunctionName = lambdaFunctionName(context);
        log.info(lambdaFunctionName + " lambda invoked.");
        List<String> validationErrorMsgList = Collections.synchronizedList(new ArrayList<>());
        List<ScanSpec> scanSpecList = Collections.synchronizedList(new ArrayList<>());

        if (sqsEvent.getRecords().isEmpty()) {
            log.error("Empty message received.");
            return;
        }

        sqsEvent.getRecords().parallelStream().forEach(record -> {

            String messageBody = record.getBody();
            String payloadReferenceMessageBody = null;
            try {
                if (StringUtils.isBlank(messageBody)) {
                    throw new AmazonPayMerchantURLInvalidInputException(
                            "Message field of SQS message is empty for messageId: " + record.getMessageId());
                }

                String vendorId = null;

                if (record.getMessageAttributes().size() > 0) {
                    if (record.getMessageAttributes().get(VENDOR_ID) != null) {
                        vendorId = record.getMessageAttributes().get(VENDOR_ID).getStringValue();
                    }

                    if (isPayloadPresentInS3(record.getMessageAttributes())) {
                        payloadReferenceMessageBody = messageBody;
                        messageBody = readMessageFromS3(messageBody);
                    }
                }

                if (vendorId == null) {
                    // TODO : Once EverCompliant starts sending vendorId, we can remove this defaulting and throw
                    //an exception or handle accordingly.
                    vendorId = EVERCOMPLIANT_ID;
                }

                ScanSpec scanSpec = vendorCallbackHandlerFactory.produceVendorHandler(vendorId)
                        .handleCallback(messageBody);
                scanSpecList.add(scanSpec);
            } catch (IOException e) {
                log.error("Exception received due to deserialization.", e);
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                        EXECUTE_VENDOR_RESPONSE_DESERIALIZATION_FAILURE);
                addMessageInValidationList(payloadReferenceMessageBody, messageBody, validationErrorMsgList);
            } catch (Exception e) {
                if (e instanceof AmazonPayMerchantURLNonRetryableException
                        | e instanceof UrlVendorReviewClientException
                        | e instanceof AmazonPayMerchantURLInvalidInputException) {
                    log.error("Non-retryable exception received.", e);
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                            EXECUTE_VENDOR_RESPONSE_VALIDATION_FAILURE);
                    addMessageInValidationList(payloadReferenceMessageBody, messageBody, validationErrorMsgList);
                } else {
                    log.error("Retryable exception received.", e);
                    throw e;
                }
            }
        });

        if (!validationErrorMsgList.isEmpty()) {
            sqsAdapter.sendMessages(validationErrorMsgList,
                    vendorResponseValidationErrorQueueUrl, sqsBatchRequestTranslator);
        }

        if (!scanSpecList.isEmpty()) {
            final UrlVendorReviewScanSpecInput urlVendorReviewScanSpecInput =
                    buildUrlVendorReviewScanSpecInput(scanSpecList);
            callbackWorkflowDeterminatorTask.initateOrResumeURLReviewWorkflow(urlVendorReviewScanSpecInput);
        }

        return;
    }

    /**
     * isPayloadPresentInS3 checks if the actual payload is pushed to S3 based on the message attribute.
     * ExtendedPayloadSize/SQSLargePayloadSize is used as attributes for large payload by the SQSExtendedClientLibrary.
     * @param messageAttributeMap       message attribute map.
     * @return                          true/false based on attributes.
     */
    private boolean isPayloadPresentInS3(final Map<String, MessageAttribute> messageAttributeMap) {
        return (messageAttributeMap.get(EXTENDED_PAYLOAD_SIZE) != null)
                || (messageAttributeMap.get(SQS_LARGE_PAYLOAD_SIZE) != null);
    }

    private void addMessageInValidationList(final String payloadReferenceMessageBody, final String messageBody,
                                            List<String> validationErrorMsgList) {
        final String message = (payloadReferenceMessageBody != null) ? payloadReferenceMessageBody : messageBody;
        validationErrorMsgList.add(message);
    }

    /**
     * readMessageFromS3 reads the actual payload from S3.
     * Large payload message JSON returned by the SQS is an array where
     * first element is the class name (com.amazon.sqs.javamessaging.MessageS3Pointer)
     * or (software.amazon.payloadoffloading.PayloadS3Pointer)
     * second element is the content we want s3BucketName and s3Key.
     *
     * eg.
     * [
     *   "com.amazon.sqs.javamessaging.MessageS3Pointer",
     *   {
     *     "s3BucketName": "test-bucket",
     *     "s3Key": "test-key"
     *   }
     * ]
     *
     * As the above mentioned class is private, we need to use JSON parsing for deserialization
     * using our model for MessageS3Pointer.
     *
     * @param messageBody     message body with payload reference.
     * @return                payload from S3
     * @throws IOException
     */
    private String readMessageFromS3(final String messageBody) throws IOException {
        String modifiedMessageBody;
        if (messageBody.contains(EXTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS)) {
            modifiedMessageBody = messageBody.replace(EXTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS,
                    INTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS);
        } else if (messageBody.contains(EXTERNAL_PAYLOAD_S3_POINTER_MODEL_CLASS)) {
            modifiedMessageBody = messageBody.replace(EXTERNAL_PAYLOAD_S3_POINTER_MODEL_CLASS,
                    INTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS);
        } else {
            throw new AmazonPayMerchantURLNonRetryableException("Message reference payload class name not found."
                    + messageBody);
        }

        final MessageS3Pointer messageS3Pointer = readMessageS3PointerFromJSON(modifiedMessageBody);
        return s3Adapter.getRecords(messageS3Pointer.getS3BucketName(), messageS3Pointer.getS3Key());
    }

    /**
     * Deserializes message body using our MessageS3Pointer.
     * @param messageBody        message body containting the reference to payload in S3
     * @return                   MessagePointer
     * @throws IOException
     */
    private MessageS3Pointer readMessageS3PointerFromJSON(final String messageBody) throws IOException {
        return objectMapper.readValue(messageBody, MessageS3Pointer.class);
    }

    private UrlVendorReviewScanSpecInput buildUrlVendorReviewScanSpecInput(final List<ScanSpec> scanSpecList) {
        return UrlVendorReviewScanSpecInput.builder()
                .retryCount(0L)
                .scanSpecList(scanSpecList)
                .build();
    }
}
