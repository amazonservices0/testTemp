package com.amazon.amazonpaymerchanturl.factory;

import com.amazon.amazonpaymerchanturl.constants.PostUrlReviewActionTaskType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.task.GetAllFailedWorkflowsTask;
import com.amazon.amazonpaymerchanturl.task.PostUrlReviewActionBaseTask;
import com.amazon.amazonpaymerchanturl.task.RetryWorkflowsTask;
import com.amazon.amazonpaymerchanturl.task.StartManualWorkflowTask;
import com.amazon.amazonpaymerchanturl.task.UpdateAndSendNotificationInGivenRangeTask;
import com.amazon.amazonpaymerchanturl.task.UpdateAndSendNotificationTask;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Singleton;

/**
 * This factory class will return the corresponding task handler to execute.
 */
@Log4j2
@Singleton
public class PostUrlReviewActionTaskFactory {
    private final UpdateAndSendNotificationTask updateAndSendNotificationTaskHandler;
    private final UpdateAndSendNotificationInGivenRangeTask updateAndSendNotificationInGivenRangeTaskHandler;
    private final GetAllFailedWorkflowsTask getAllFailedWorkflowsTask;
    private final RetryWorkflowsTask retryWorkflowsTaskHandler;
    private final StartManualWorkflowTask startManualWorkflowTask;

    public PostUrlReviewActionTaskFactory(
            final UpdateAndSendNotificationTask updateAndSendNotificationTaskHandler,
            final UpdateAndSendNotificationInGivenRangeTask updateAndSendNotificationInGivenRangeTaskHandler,
            final GetAllFailedWorkflowsTask getAllFailedWorkflowsTask,
            final RetryWorkflowsTask retryWorkflowsTaskHandler,
            final StartManualWorkflowTask startManualWorkflowTask) {
        this.updateAndSendNotificationTaskHandler = updateAndSendNotificationTaskHandler;
        this.updateAndSendNotificationInGivenRangeTaskHandler = updateAndSendNotificationInGivenRangeTaskHandler;
        this.getAllFailedWorkflowsTask = getAllFailedWorkflowsTask;
        this.retryWorkflowsTaskHandler = retryWorkflowsTaskHandler;
        this.startManualWorkflowTask = startManualWorkflowTask;
    }

    /**
     * Produces the postUrlReviewAction task handler
     * @param taskType           task type.
     * @return                   returns task specific Handler.
     */
    public PostUrlReviewActionBaseTask produceTaskHandler(@NonNull final String taskType) {
        log.info("Producing PostUrlReviewActionHandler for task type : " + taskType);
        switch (PostUrlReviewActionTaskType.fromValue(taskType)) {
            case UPDATE_AND_SEND_NOTIFICATION:
                return updateAndSendNotificationTaskHandler;

            case UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_DATE_RANGE:
                return updateAndSendNotificationInGivenRangeTaskHandler;

            case GET_ALL_FAILED_WORKFLOW:
                return getAllFailedWorkflowsTask;

            case RETRY_WORKFLOW:
                return retryWorkflowsTaskHandler;

            case START_MANUAL_WORKFLOW:
                return startManualWorkflowTask;

            default :
                throw new AmazonPayMerchantURLNonRetryableException(" taskType : '" + taskType + "' is not supported");
        }
    }
}
