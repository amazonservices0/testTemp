package com.amazon.amazonpaymerchanturl.module;

import com.amazon.amazonpaymerchanturl.factory.PostUrlReviewActionTaskFactory;
import com.amazon.amazonpaymerchanturl.task.GetAllFailedWorkflowsTask;
import com.amazon.amazonpaymerchanturl.task.RetryWorkflowsTask;
import com.amazon.amazonpaymerchanturl.task.StartManualWorkflowTask;
import com.amazon.amazonpaymerchanturl.task.UpdateAndSendNotificationInGivenRangeTask;
import com.amazon.amazonpaymerchanturl.task.UpdateAndSendNotificationTask;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.GET_ALL_FAILED_WORKFLOWS_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.POST_URL_REVIEW_ACTION_TASK_FACTORY;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.RETRY_WORKFLOWS_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.START_MANUAL_WORKFLOW_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_RANGE_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.UPDATE_AND_SEND_NOTIFICATION_TASK;

/**
 * Service configuration class for factory module.
 */
@Module
public class FactoryModule {
    /**
     * Provides  PostUrlReviewActionTask factory.
     * @param updateAndSendNotificationTask                 the updateAndSendNotification task
     * @param updateAndSendNotificationInGivenRangeTask     the updateAndSendNotificationInGivenRange task
     * @param getAllFailedWorkflowsTask                     the getAllFailedWorkflowsTask
     * @param retryWorkflowsTask                            the retryWorkflowsTask
     * @return                                              postUrlReviewActionTask factory
     */
    @Singleton
    @Provides
    @Named(POST_URL_REVIEW_ACTION_TASK_FACTORY)
    public PostUrlReviewActionTaskFactory providesPostUrlReviewActionTaskFactory(
            @Named(UPDATE_AND_SEND_NOTIFICATION_TASK)
            final UpdateAndSendNotificationTask updateAndSendNotificationTask,
            @Named(UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_RANGE_TASK)
            final UpdateAndSendNotificationInGivenRangeTask updateAndSendNotificationInGivenRangeTask,
            @Named(GET_ALL_FAILED_WORKFLOWS_TASK)
            final GetAllFailedWorkflowsTask getAllFailedWorkflowsTask,
            @Named(RETRY_WORKFLOWS_TASK) final RetryWorkflowsTask retryWorkflowsTask,
            @Named(START_MANUAL_WORKFLOW_TASK) final StartManualWorkflowTask startManualWorkflowTask) {
        return new PostUrlReviewActionTaskFactory(updateAndSendNotificationTask,
                updateAndSendNotificationInGivenRangeTask, getAllFailedWorkflowsTask, retryWorkflowsTask,
                startManualWorkflowTask);
    }
}
