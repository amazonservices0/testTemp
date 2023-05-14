package com.amazon.amazonpaymerchanturl.translator;

import static java.util.UUID.randomUUID;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;

import lombok.NonNull;

/**
 * Batch request translator for sending messages to queue.
 */
public class SQSBatchRequestTranslator implements ITranslator<List<String>, SendMessageBatchRequest> {

    @Inject
    public SQSBatchRequestTranslator() {
    }

    /**
     * Translates message list to send message batch request.
     * @param messageList list of messages
     * @return SendMessageBatchRequest
     */
    @Override
    public SendMessageBatchRequest translate(@NonNull final List<String> messageList) {
        List<SendMessageBatchRequestEntry> requestEntry = messageList.stream()
                .map(msg -> createMessageBatchRequestEntry(msg))
                .collect(Collectors.toList());

        return new SendMessageBatchRequest().withEntries(requestEntry);
    }

    private SendMessageBatchRequestEntry createMessageBatchRequestEntry(String message) {
        return new SendMessageBatchRequestEntry()
                .withMessageBody(message)
                .withId(randomUUID().toString());
    }
}
