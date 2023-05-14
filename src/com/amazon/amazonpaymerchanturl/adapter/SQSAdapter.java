package com.amazon.amazonpaymerchanturl.adapter;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.POST_MESSAGE_FAILURE;

import java.util.List;
import javax.inject.Singleton;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.translator.ITranslator;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;

/**
 * SQS adapter for sending messages to queue.
 */
@Log4j2
@Singleton
public class SQSAdapter {
    private final AmazonSQS sqsClient;
    private ITranslator<List<String>, SendMessageBatchRequest> sqsBatchRequestTranslator;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;

    public SQSAdapter(final AmazonSQS sqsClient,
                      final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        this.sqsClient = sqsClient;
        this.cloudWatchMetricsHelper = cloudWatchMetricsHelper;
    }

    /**
     * Send messages to queue.
     * @param messages the messages
     * @param sqsEndpoint the url for the queue
     * @param sqsBatchRequestTranslator the sqsBatchRequestTranslator
     */
    public void sendMessages(@NonNull final List<String> messages, @NonNull final String sqsEndpoint,
        @NonNull final ITranslator<List<String>, SendMessageBatchRequest>  sqsBatchRequestTranslator) {
        try {
            if (messages.isEmpty()) {
                return;
            }

            SendMessageBatchResult result = sqsClient.sendMessageBatch(
                    sqsBatchRequestTranslator.translate(messages).withQueueUrl(sqsEndpoint));

            if (result.getFailed().isEmpty()) {
                return;
            }

            final String queueName = getQueueName(sqsEndpoint);
            result.getFailed().forEach(error -> {
                log.warn("Post message failure to queue {}. {}", queueName, error.getMessage());
            });
        } catch (Exception e) {
            logAndPublishMetric(e, sqsEndpoint);
        }
    }

    /**
     * Send message to queue.
     * @param message the message
     * @param sqsEndpoint the url for the queue
     */
    public void sendMessage(@NonNull final String message, @NonNull final String sqsEndpoint) {
        if (message.isEmpty()) {
            return;
        }

        final SendMessageRequest sendMessageRequest = buildSendMessageRequest(message, sqsEndpoint);

        try {
            sqsClient.sendMessage(sendMessageRequest);
        } catch (Exception e) {
            logAndPublishMetric(e, sqsEndpoint);
        }
    }

    private String getQueueName(final String sqsEndpoint) {
        return sqsEndpoint.substring(sqsEndpoint.lastIndexOf('/') + 1);
    }

    private SendMessageRequest buildSendMessageRequest(final String message, final String sqsEndpoint) {
        return new SendMessageRequest()
                .withMessageBody(message)
                .withQueueUrl(sqsEndpoint)
                .withDelaySeconds(900);
    }

    private void logAndPublishMetric(final Exception e, final String sqsEndpoint) {
        final String queueName = getQueueName(sqsEndpoint);
        log.warn("Post message failure to queue {}. {}", queueName, e.getMessage());
        cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(POST_MESSAGE_FAILURE + queueName);
    }
}
