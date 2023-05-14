package com.amazon.amazonpaymerchanturl.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum PostUrlReviewActionTaskType {
    /**
     * If task type is update and send notification then update both ddb and publish sns notification
     */
    UPDATE_AND_SEND_NOTIFICATION("UpdateAndSendNotification"),

    UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_DATE_RANGE("UpdateAndSendNotificationInGivenDateRange"),

    GET_ALL_FAILED_WORKFLOW("GetAllFailedWorkflows"),

    RETRY_WORKFLOW("RetryWorkflow"),

    START_MANUAL_WORKFLOW("StartManualWorkflow"),

    UNKNOWN("Unknown");

    private final String value;

    /**
     * Gets post url review action task type from input string value.
     *
     * @param input String
     * @return postUrlReviewActionTaskType
     */
    public static PostUrlReviewActionTaskType fromValue(final String input) {
        Optional<PostUrlReviewActionTaskType> postUrlReviewActionTaskType = Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(input))
                .findFirst();

        return postUrlReviewActionTaskType.orElse(PostUrlReviewActionTaskType.UNKNOWN);
    }
}
