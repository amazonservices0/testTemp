package com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions;

import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.accountId;
import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.executionRegion;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.DLQ_MSG_DELETE_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.DLQ_MSG_SEND_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.QUEUE_MSG_DELETE_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.DLQ_URL_FORMAT;

//TODO:  Add CSM if required in all the AWS clients.

/**
 * Holds various Util functions for AWS SQS service.
 */
@Log4j2
public final class SQS {
    private SQS() {
    }

    @Builder
    public static class DeleteMsgFromLambdaDLQInput {
        private AmazonSQS sqsClient;
        private String receiptHandle, lambdaName;
        private CloudWatchMetricsHelper cloudWatchMetricsHelper;
        private Context context;
    }

    public static boolean deleteMsgFromLambdaDLQ(DeleteMsgFromLambdaDLQInput input) {
        final String url = dlqURL(input.lambdaName, input.context);
        final DeleteMessageRequest deleteRequest = new DeleteMessageRequest()
                .withQueueUrl(url).withReceiptHandle(input.receiptHandle);

        log.debug("deleting message with handle: {} from DLQ: {} ", input.receiptHandle, url);
        try {
            input.sqsClient.deleteMessage(deleteRequest);
        } catch (Exception e) {
            input.cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(DLQ_MSG_DELETE_FAILED);
            log.error("deletion of  message with handle: {} from queue: {} failed: {}", input.receiptHandle, url, e);
            return false;
        }
        return true;
    }

    @Builder
    public static class SendMsgToLambdaDLQInput {
        private AmazonSQS sqsClient;
        private String jsonMsg, lambdaName;
        private CloudWatchMetricsHelper cloudWatchMetricsHelper;
        private Context context;
    }

    public static boolean sendMsgToLambdaDLQ(SendMsgToLambdaDLQInput input) {
        final String dlqURL = SQS.dlqURL(input.lambdaName, input.context);
        log.debug("sending message: {} to queue: {} ", input.jsonMsg, dlqURL);
        final SendMessageRequest msgReq = new SendMessageRequest().withMessageBody(input.jsonMsg).
                withQueueUrl(dlqURL).withDelaySeconds(900);
        try {
            input.sqsClient.sendMessage(msgReq);
        } catch (Exception e) {
            input.cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(DLQ_MSG_SEND_FAILED);
            log.error("unable to send message: {}. error: {}", input.jsonMsg, e);
            return false;
        }
        return true;
    }

    @Builder
    public static class DeleteMsgFromLambdaQueueInput {
        private AmazonSQS sqsClient;
        private String receiptHandle;
        private CloudWatchMetricsHelper cloudWatchMetricsHelper;
        private String queueUrl;
    }

    public static boolean deleteMsgFromLambdaQueue(DeleteMsgFromLambdaQueueInput deleteMsgFromLambdaQueueInput) {
        final String receiptHandle = deleteMsgFromLambdaQueueInput.receiptHandle;
        final String queueUrl = deleteMsgFromLambdaQueueInput.queueUrl;

        final DeleteMessageRequest deleteRequest = new DeleteMessageRequest()
                .withQueueUrl(queueUrl)
                .withReceiptHandle(receiptHandle);

        log.debug("Deleting message with handle: {} from Queue: {} ", receiptHandle, queueUrl);
        try {
            deleteMsgFromLambdaQueueInput.sqsClient.deleteMessage(deleteRequest);
        } catch (Exception e) {
            deleteMsgFromLambdaQueueInput.cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    QUEUE_MSG_DELETE_FAILED);
            log.error("Deletion of message with handle: {} from queue: {} failed: {}", receiptHandle, queueUrl, e);
            return false;
        }
        return true;
    }

    private static String dlqURL(String lambdaName, Context context) {
        return String.format(DLQ_URL_FORMAT, executionRegion(context), accountId(context), lambdaName);
    }

    //Note - shutdown() is not called on SQS client because on class unload resources will be freed implicitly.
}
