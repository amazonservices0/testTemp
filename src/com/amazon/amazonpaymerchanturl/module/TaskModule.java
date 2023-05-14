package com.amazon.amazonpaymerchanturl.module;

import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazon.amazonpaymerchanturl.task.CallbackWorkflowDeterminatorTask;
import com.amazon.amazonpaymerchanturl.task.GetAllFailedWorkflowsTask;
import com.amazon.amazonpaymerchanturl.task.RetryWorkflowsTask;
import com.amazon.amazonpaymerchanturl.task.StartManualWorkflowTask;
import com.amazon.amazonpaymerchanturl.task.UpdateAndSendNotificationInGivenRangeTask;
import com.amazon.amazonpaymerchanturl.task.UpdateAndSendNotificationTask;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;

import dagger.Module;
import dagger.Provides;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.GET_ALL_FAILED_WORKFLOWS_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.OBJECT_MAPPER;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.RETRY_WORKFLOWS_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.START_MANUAL_WORKFLOW_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_RANGE_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.UPDATE_AND_SEND_NOTIFICATION_TASK;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.URL_REVIEW_WORKFLOW_MAP;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.URL_STATUS_SNS_ADAPTER;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.URL_STATUS_TOPIC;

/**
 * Service configuration class for Task module.
 */
@Module
public class TaskModule {

    /**
     * Provides callback workflow determinator task.
     * @param domainValidationDDBAdapter           the domain validation ddb adapter.
     * @param urlInvestigationDDBAdapter           the url investigation ddb adapter.
     * @param cloudWatchMetricsHelper              the cloud watch metrics helper.
     * @param stepFunctionAdapter                  the step function adapter.
     * @param urlReviewWorkflowMap                 the url review workflow map.
     * @param objectMapper                         the object mapper.
     * @param sqsAdapter                           the sqs adapter
     * @param queueUrl                             the sqs queue url.
     * @return CallbackWorkflowDeterminatorTask    the callback workflow determinator task.
     */
    @Singleton
    @Provides
    //CHECKSTYLE:SUPPRESS:ParameterNumber
    public CallbackWorkflowDeterminatorTask providesCallbackWorkflowDeterminatorTask(
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper,
            final StepFunctionAdapter stepFunctionAdapter,
            @Named(URL_REVIEW_WORKFLOW_MAP)
            final Map<String, String> urlReviewWorkflowMap,
            @Named(OBJECT_MAPPER) final ObjectMapper objectMapper,
            final SQSAdapter sqsAdapter,
            @Named(EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL) final String queueUrl,
            final WeblabHelper weblabHelper) {
        return new CallbackWorkflowDeterminatorTask(domainValidationDDBAdapter, urlInvestigationDDBAdapter,
                cloudWatchMetricsHelper, stepFunctionAdapter, urlReviewWorkflowMap, objectMapper, sqsAdapter, queueUrl,
                weblabHelper);
    }
    //CHECKSTYLE:UNSUPPRESS:ParameterNumber

    /**
     * Provides updateAndSendNotification handler.
     * @return                         the updateAndSendNotification handler.
     */
    @Singleton
    @Provides
    @Named(UPDATE_AND_SEND_NOTIFICATION_TASK)
    public UpdateAndSendNotificationTask providesUpdateAndSendNotificationTaskHandler(
            @Named(OBJECT_MAPPER) final ObjectMapper objectMapper,
            final StepFunctionAdapter stepFunctionAdapter,
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            @Named(URL_STATUS_SNS_ADAPTER) final SNSAdapter snsAdapter,
            @Named(URL_STATUS_TOPIC) final String urlStatusNotificationTopic,
            @Named(URL_REVIEW_WORKFLOW_MAP) final Map<String, String> urlReviewWorkflowMap) {
        return new UpdateAndSendNotificationTask(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter,
                urlInvestigationDDBAdapter, snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    /**
     * Provides updateAndSendNotificationInGivenRange handler.
     * @return                         the updateAndSendNotificationInGivenRange handler.
     */
    @Singleton
    @Provides
    @Named(UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_RANGE_TASK)
    public UpdateAndSendNotificationInGivenRangeTask providesUpdateAndSendNotificationInGivenRangeTaskHandler(
            @Named(OBJECT_MAPPER) final ObjectMapper objectMapper,
            final StepFunctionAdapter stepFunctionAdapter,
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            @Named(URL_STATUS_SNS_ADAPTER) final SNSAdapter snsAdapter,
            @Named(URL_STATUS_TOPIC) final String urlStatusNotificationTopic,
            @Named(URL_REVIEW_WORKFLOW_MAP) final Map<String, String> urlReviewWorkflowMap) {
        return new UpdateAndSendNotificationInGivenRangeTask(objectMapper, stepFunctionAdapter,
                domainValidationDDBAdapter, urlInvestigationDDBAdapter, snsAdapter, urlStatusNotificationTopic,
                urlReviewWorkflowMap);
    }

    /**
     * Provides getAllFailedWorkflows handler.
     * @return                         the getAllFailedWorkflows handler.
     */
    @Singleton
    @Provides
    @Named(GET_ALL_FAILED_WORKFLOWS_TASK)
    public GetAllFailedWorkflowsTask providesGetAllFailedWorkflowsTaskHandler(
            @Named(OBJECT_MAPPER) final ObjectMapper objectMapper,
            final StepFunctionAdapter stepFunctionAdapter,
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            @Named(URL_STATUS_SNS_ADAPTER) final SNSAdapter snsAdapter,
            @Named(URL_STATUS_TOPIC) final String urlStatusNotificationTopic,
            @Named(URL_REVIEW_WORKFLOW_MAP) final Map<String, String> urlReviewWorkflowMap) {
        return new GetAllFailedWorkflowsTask(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter,
                urlInvestigationDDBAdapter, snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    /**
     * Provides retryWorkflows handler.
     * @return                         the retryWorkflows handler.
     */
    @Singleton
    @Provides
    @Named(RETRY_WORKFLOWS_TASK)
    public RetryWorkflowsTask providesRetryWorkflowsTaskHandler(
            @Named(OBJECT_MAPPER) final ObjectMapper objectMapper,
            final StepFunctionAdapter stepFunctionAdapter,
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            @Named(URL_STATUS_SNS_ADAPTER) final SNSAdapter snsAdapter,
            @Named(URL_STATUS_TOPIC) final String urlStatusNotificationTopic,
            @Named(URL_REVIEW_WORKFLOW_MAP) final Map<String, String> urlReviewWorkflowMap) {
        return new RetryWorkflowsTask(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter,
                urlInvestigationDDBAdapter, snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }

    /**
     * Provides startManualWorkflow handler.
     * @return                         the startManualWorkflow handler.
     */
    @Singleton
    @Provides
    @Named(START_MANUAL_WORKFLOW_TASK)
    public StartManualWorkflowTask providesStartManualWorkflowTaskHandler(
            @Named(OBJECT_MAPPER) final ObjectMapper objectMapper,
            final StepFunctionAdapter stepFunctionAdapter,
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            @Named(URL_STATUS_SNS_ADAPTER) final SNSAdapter snsAdapter,
            @Named(URL_STATUS_TOPIC) final String urlStatusNotificationTopic,
            @Named(URL_REVIEW_WORKFLOW_MAP) final Map<String, String> urlReviewWorkflowMap) {
        return new StartManualWorkflowTask(objectMapper, stepFunctionAdapter, domainValidationDDBAdapter,
                urlInvestigationDDBAdapter, snsAdapter, urlStatusNotificationTopic, urlReviewWorkflowMap);
    }
}
