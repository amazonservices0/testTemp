package com.amazon.amazonpaymerchanturl.constants;

import java.util.HashMap;
import java.util.Map;

import com.amazon.apurlvalidation.constant.UrlStatus;

/**
 * Constants for Metrics Adapter.
 */
public final class MetricConstants {

    private MetricConstants() {
    }

    /**
     * Paragon Investigation Service Error Metric.
     */
    public static final String PARAGON_INVESTIGATION_ERROR_METRIC
            = "ParagonInvestigationErrorMetric";

    /**
     * Stego Service GetLwaApplication Call Unsupported Account Type Error Metric.
     */
    public static final String STEGO_SERVICE_GET_LWA_APP_ERROR_METRIC
            = "StegoServiceGetLWAApplicationErrorMetric";

    /**
     * Stego Service GetLwaApplication Call Unsupported Account Type Error Metric.
     */
    public static final String STEGO_SERVICE_UPDATE_LWA_APP_ERROR_METRIC
            = "StegoServiceUpdateLWAApplicationErrorMetric";

    /**
     * Success Metrics for the TriggerUrlReview lambda.
     */
    public static final String TRIGGER_URL_REVIEW_SUCCESS_METRICS = "TriggerUrlReviewSuccessMetrics";

    /**
     * Failure Metrics for the TriggerUrlReview lambda.
     */
    public static final String TRIGGER_URL_REVIEW_FAILURE_METRICS = "TriggerUrlReviewFailureMetrics";

    /**
     * Success Metrics for the ManualUrlReview lambda.
     */
    public static final String MANUAL_URL_REVIEW_SUCCESS_METRICS = "ManualUrlReviewSuccessMetrics";

    /**
     * Failure Metrics for the ManualUrlReview lambda.
     */
    public static final String MANUAL_URL_REVIEW_FAILURE_METRICS = "ManualUrlReviewFailureMetrics";

    /**
     * Success Metrics for ExecuteUrlReviewWorkflow lambda.
     */
    public static final String EXECUTE_URL_REVIEW_WORKFLOW_SUCCESS_METRICS = "ExecuteUrlReviewWorkflowSuccessMetrics";

    /**
     * Failure Metrics for ExecuteUrlReviewWorkflow lambda.
     */
    public static final String EXECUTE_URL_REVIEW_WORKFLOW_FAILURE_METRICS = "ExecuteUrlReviewWorkflowFailureMetrics";

    /**
     * Metrics for undefined UrlReview Workflow.
     */
    public static final String URL_REVIEW_WORKFLOW_NOT_AVAILABLE_METRICS = "UrlReviewWorkflowNotAvailableMetrics";

    /**
     * Success Metrics for the ProcessInvestigation lambda.
     */
    public static final String PROCESS_INVESTIGATION_SUCCESS_METRICS = "ProcessInvestigationSuccessMetrics";

    /**
     * Failure Metrics for the ProcessInvestigation lambda.
     */
    public static final String PROCESS_INVESTIGATION_FAILURE_METRICS = "ProcessInvestigationFailureMetrics";

    /**
     * Success Metrics for the ProcessManualInvestigation lambda.
     */
    public static final String PROCESS_MANUAL_INVESTIGATION_SUCCESS_METRICS
            = "ProcessManualInvestigationSuccessMetrics";

    /**
     * Failure Metrics for the ProcessManualInvestigation lambda.
     */
    public static final String PROCESS_MANUAL_INVESTIGATION_FAILURE_METRICS
            = "ProcessManualInvestigationFailureMetrics";

    /**
     * Success Metrics for the ExecuteManualResponseWorkflow lambda.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_SUCCESS_METRICS
            = "ExecuteManualResponseWorkflowSuccessMetrics";

    /**
     * Failure Metrics for the ExecuteManualResponseWorkflow lambda.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_METRICS
            = "ExecuteManualResponseWorkflowFailureMetrics";

    /**
     * For Invalid Task Token in the ExecuteManualResponseWorkflow lambda.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_INVALID_TASK_TOKEN
            = "ExecuteManualResponseWorkflow_InvalidTaskToken";

    /**
     * For Invalid Task Token in the ExecuteManualResponseWorkflow lambda.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_RETRYABLE_ERROR
            = "ExecuteManualResponseWorkflow_Retryable_Error";

    /**
     * Success Metrics for the InitiateVendorReview lambda.
     */
    public static final String INITIATE_VENDOR_REVIEW_SUCCESS_METRICS
            = "InitiateVendorReviewSuccessMetrics";

    /**
     * Failure Metrics for the InitiateVendorReview lambda.
     */
    public static final String INITIATE_VENDOR_REVIEW_FAILURE_METRICS
            = "InitiateVendorReviewFailureMetrics";

    /**
     * DDB entry not found.
     */
    public static final String DDB_ENTRY_NOT_FOUND = "DDBEntryNotFoundMetrics";

    /**
     * Failed to parse the SQS Event.
     */
    public static final String SQS_EVENT_PARSING_FAILED = "SQSEventParsingFailed";

    /**
     * When SQS event has null records.
     */
    public static final String SQS_EVENT_NULL_RECORDS = "SQSEventNullRecords";

    /**
     * Not able to classified the SQS Message.
     */
    public static final String SQS_MSG_NOT_CLASSIFIED = "SQSMessageNotClassified";

    /**
     * SQS Message failed to retry count increment.
     */
    public static final String SQS_MSG_RETRY_COUNT_INCREMENT_FAILED = "SQSMessageRetryCountIncrementFailed";

    /**
     * SQS message retry count lower limit has been crossed.
     */
    public static final String SQS_MSG_RETRY_LOWER_LIMIT_CROSSED = "SQSMessageRetryLowerLimitCrossed";

    /**
     * SQS message retry count upper limit has been crossed.
     */
    public static final String SQS_MSG_RETRY_UPPER_LIMIT_CROSSED = "SQSMessageRetryUpperLimitCrossed";

    /**
     * Lambda Output stream failed to write.
     */
    public static final String LAMBDA_OUTPUT_STREAM_WRITE_FAILED = "LambdaOutputStreamWriteFailed";

    /**
     * Url status notification failued to publish.
     */
    public static final String URL_STATUS_NOTIFICATION_TOPIC_FAILED = "UrlStatusNotificationTopicFailed";

    /**
     * lambda output stream failed to close the writer operation.
     */
    public static final String LAMBDA_OUTPUT_STREAM_WRITER_CLOSE_FAILED =
            "LambdaOutputStreamWriterCloseOperationFailed";

    /**
     * Asynchronous Invocation of lambda function failed.
     */
    public static final String LAMBDA_ASYNC_INVOCATION_FAILED = "LambdaAsyncInvocationFailed";

    /**
     * Delete operation of DLQ Message failed.
     */
    public static final String DLQ_MSG_DELETE_FAILED = "DLQMessageDeleteOperationFailed";

    /**
     * Send operation of DLQ message Failed.
     */
    public static final String DLQ_MSG_SEND_FAILED = "DLQMessageSendOperationFailed";

    /**
     * For the Default handler Unknown Exception caught.
     */
    public static final String DEFAULT_HANDLER_UNKNOWN_EXCEPTION = "DefaultHandlerUnknownException";

    /**
     * Post message failure queue.
     */
    public static final String POST_MESSAGE_FAILURE = "PostMessageFailure_";

    /**
     * Failed to fetch the treatments from weblab
     */
    public static final String WEBLAB_TREATMENT_FETCH_FAILED = "WeblabTreatmentFetchOperationFailed";

    /**
     * Execute vendor response flow for auto monitoring when url in review.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_AUTO_MONITORING_URL =
            "ExecuteVendorResponse_AutoMonitoring_Url_";

    /**
     * Execute vendor response flow no entry in DomainValidation DDB.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_NO_ENTRY_IN_DOMAIN_VALIDATION_DDB =
            "ExecuteVendorResponse_NoEntry_In_DomainValidationDDB";

    /**
     * Execute vendor response flow no entry in UrlInvestigation DDB.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_NO_ENTRY_IN_URL_INVESTIGATION_DDB =
            "ExecuteVendorResponse_NoEntry_In_UrlInvestigationDDB";

    /**
     * Execute vendor response flow no task token in UrlInvestigation DDB.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_NO_TASK_TOKEN =
            "ExecuteVendorResponse_NoTaskToken";

    /**
     * Execute vendor response deserialization failure.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_DESERIALIZATION_FAILURE =
            "ExecuteVendorResponseDeserializationFailure";

    /**
     * Execute vendor response validation failure.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_VALIDATION_FAILURE = "ExecuteVendorResponseValidationFailure";

    /**
     * Execute vendor response flow, investigation id is null in DomainValidation DDB.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_INVESTIGATION_ID_NULL_IN_DOMAIN_VALIDATION_DDB =
            "ExecuteVendorResponse_InvestigationId_NULL_In_DomainValidationDDB";

    /**
     * Execute vendor response flow, investigation status is null in DomainValidation DDB.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_INVESTIGATION_STATUS_NULL_IN_DOMAIN_VALIDATION_DDB =
            "ExecuteVendorResponse_InvestigationStatus_NULL_In_DomainValidationDDB";

    /**
     * Number of urls AutoApproved.
     */
    public static final String AUTO_APPROVED_URL_METRIC = "AutoApprovedURLMetric";

    /**
     * Number of urls AutoDenied
     */
    public static final String AUTO_DENIED_URL_METRIC = "AutoDeniedURLMetric";

    /**
     * Number of urls go for Manual Investigation.
     */
    public static final String MANUAL_REVIEW_URL_METRIC = "ManualReviewURLMetric";

    /**
     * UrlValidationStatusMetricsMap store Metrics name corresponding to url status.
     * @return map
     */
    public static Map<UrlStatus, String> getUrlValidationStatusMetricMap() {
        Map<UrlStatus, String> upfrontUrlValidationStatusMap = new HashMap<>();
        upfrontUrlValidationStatusMap.put(UrlStatus.MANUAL, MANUAL_REVIEW_URL_METRIC);
        upfrontUrlValidationStatusMap.put(UrlStatus.ALLOWED, AUTO_APPROVED_URL_METRIC);
        upfrontUrlValidationStatusMap.put(UrlStatus.DENIED, AUTO_DENIED_URL_METRIC);
        return upfrontUrlValidationStatusMap;
    }

    /**
     * Execute vendor response flow, error in initiating url review workflow.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_INITIATE_URL_REVIEW_WORKFLOW_ERROR =
            "ExecuteVendorResponse_Initiate_UrlReviewWorkflow_Error";

    /**
     * Failure Metrics for process vendor response lambda.
     */
    public static final String PROCESS_VENDOR_RESPONSE_FAILURE_METRICS = "ProcessVendorResponseFailureMetrics";

    /**
     * Execute vendor response flow, error in resuming url review workflow.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_RESUME_URL_REVIEW_WORKFLOW_ERROR =
            "ExecuteVendorResponse_Resume_UrlReviewWorkflow_Error";

    /**
     * Execute vendor response flow, duplicate request to resuming url review workflow .
     */
    public static final String EXECUTE_VENDOR_RESPONSE_RESUME_URL_REVIEW_DUPLICATE_REQUEST =
            "ExecuteVendorResponse_Resume_UrlReviewWorkflow_DuplicateRequest";

    /**
     * Execute vendor response flow, error while getting the workflow status.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_GET_WORKFLOW_STATUS_ERROR
            = "ExecuteVendorResponse_Get_WorkflowStatus_Error";

    /**
     * Success Metrics for the GetLatestVendorReviewResponse lambda.
     */
    public static final String GET_LATEST_VENDOR_REVIEW_RESPONSE_SUCCESS_METRICS
            = "GetLatestVendorReviewResponseSuccessMetrics";

    /**
     * Failure Metrics for the GetLatestVendorReviewResponse lambda.
     */
    public static final String
            GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS
            = "GetLatestVendorReviewResponseFailureMetrics";

    /**
     * Failure Metrics Bad Request for the GetLatestVendorReviewResponse lambda.
     */
    public static final String GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS_BAD_REQUEST
            = "GetLatestVendorReviewResponseFailureMetrics_BadRequest";


    /**
     * Failure Metrics DDB Entry Not Found for the GetLatestVendorReviewResponse lambda.
     */
    public static final String
            GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS_DDB_ENTRY_NOT_FOUND
            = "GetLatestVendorReviewResponseFailureMetrics_DDB_Entry_NotFound";

    /**
     * Delimiter for workflow function specific metrics.
     */
    public static final String UNDERSCORE = "_";

    /**
     * Identifier for Dlq.
     */
    public static final String DLQ = "Dlq";

    /**
     * Delete operation of Queue Message failed.
     */
    public static final String QUEUE_MSG_DELETE_FAILED = "QueueMessageDeleteOperationFailed";

    /**
     * Delete url failure metrics when ddb entry is not present.
     */
    public static final String DELETE_URL_FAILURE_METRICS_DDB_ENTRY_NOT_FOUND
            = "DeleteUrlFailureMetrics_DDB_Entry_NotFound";

    /**
     * Delete url failure metrics for internal server error.
     */
    public static final String DELETE_URL_FAILURE_METRICS_INTERNAL_SERVER_ERROR
            = "DeleteUrlFailureMetrics_InternalServerError";

    /**
     * Delete merchant failure metrics for server error.
     */
    public static final String DELETE_MERCHANT_FAILURE_METRICS_SERVER_ERROR
            = "DeleteMerchantFailureMetrics_ServerError";

    /**
     * Process Delete Url failure metrics for server error.
     */
    public static final String PROCESS_DELETE_URL_FAILURE_METRICS_SERVER_ERROR
            = "ProcessDeleteUrlFailureMetrics_ServerError";
}
