package com.amazon.amazonpaymerchanturl.constants;

public final class ModuleConstants {
    private ModuleConstants() { }

    /**
     * Defines the namespace in which cloudwatch metric will be emitted.
     */
    public static final String METRIC_NAMESPACE = "APay/DomainValidation";

    /**
     * Paragon Investigation Service.
     */
    public static final String PARAGON_SVC_BASE = "ParagonInvestigationService#Base";

    /**
     * Stego Service.
     */
    public static final String STEGO_SVC_BASE = "StegoService#Base";

    /**
     * Config Override.
     */
    public static final String CONFIG_OVERRIDE
            = "{\"httpClient\":{\"connectionTimeout\":5000,\"connectionRetries\":10}}";

    /**
     * Process Investigation Response Lambda Name.
     */
    public static final String PROCESS_INVESTIGATION_RESPONSE_LAMBDA = "processInvestigationResponse";

    /**
     * Trigger Url Review Lambda Name.
     */
    public static final String TRIGGER_URL_REVIEW_LAMBDA = "triggerURLReview";

    /**
     * Execute Url Review Lambda Name.
     */
    public static final String EXECUTE_URL_REVIEW_WORKFLOW = "executeUrlReviewWorkflow";

    /**
     * Execute Manual Response Workflow Lambda Name.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW = "executeManualResponseWorkflow";

    /**
     * Poller Suffix.
     */
    public static final String POLLER_SUFFIX = "DLQPoller";

    /**
     * Dead Letter Queue URL Format.
     */
    public static final String DLQ_URL_FORMAT = "https://sqs.%s.amazonaws.com/%s/domain-validation-%s-dlq";

    /**
     * Define a Stage string constant which contains the stage value like Beta/Gamma/Prod.
     */
    public static final String STAGE = "Stage";

    /**
     * It contains the Realm string ad value associated with that is USAmazon, EUAmazon etc.
     */
    public static final String REALM = "Realm";

    /**
     * Defined for the '.' dot.
     */
    public static final String DELIMITER = ".";

    /**
     * Defines which qualifier we need.
     */
    public static final String CLOUD_AUTH = "CloudAuth";

    /**
     * Define for 'USAmazon' realm value.
     */
    public static final String US_AMAZON = "USAmazon";

    /**
     * Define the beta stage as a 'test'.
     */
    public static final String BETA_STAGE = "test";

    /**
     * Define the Map which contains the QueueId of the particular domain and realm.
     */
    public static final String QUEUE_ID_MAP = "QueueIdMap";

    /**
     * Define the Map which contains the QueueId of the particular domain and risk level.
     */
    public static final String QUEUE_ID_TO_RISK_LEVEL_MAP = "QueueIdToRiskLevelMap";

    /**
     * Define the Map which contains the QueueId of the particular domain and risk level.
     */
    public static final String QUEUE_ID_FOR_PERIODIC_REVIEW_TO_RISK_LEVEL_MAP
            = "QueueIdForPeriodicReviewToRiskLevelMap";

    /**
     * Define the Map which contains the UrlReviewWorkflow name of the particular realm.
     */
    public static final String URL_REVIEW_WORKFLOW_MAP = "urlReviewWorkflowMap";

    /**
     * Define the merchant url status notification topic.
     */
    public static final String URL_STATUS_TOPIC = "UrlStatusNotificationTopic";

    public static String getHTTPClientConfigOverride(final String base, final String qualifier) {
        return base + DELIMITER + qualifier + " : " + CONFIG_OVERRIDE;
    }

    /**
     * Defines the validation error queue url for executeVendorResponseWF lambda.
     */
    public static final String VENDOR_RESPONSE_VALIDATION_ERROR_QUEUE_URL
            = "VendorResponseValidationErrorQueueUrl";

    /**
     * Defines AWS default region.
     */
    public static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";

    /**
     * Defines the error queue url for executeVendorResponseWorkflow lambda.
     */
    public static final String EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL
            = "ExecuteVendorResponseWorkflowErrorQueueUrl";

    /**
     * Defines the dlq url for executeUrlReviewWorkflow lambda.
     */
    public static final String EXECUTE_URL_REVIEW_WORKFLOW_DLQ_URL
            = "ExecuteUrlReviewWorkflowDlqUrl";

    /**
     * Defines the dlq url for executeManualResponseWorkflow lambda.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_DLQ_URL
            = "ExecuteManualResponseWorkflowDlqUrl";

    /**
     * Defines object mapper.
     */
    public static final String OBJECT_MAPPER
            = "ObjectMapper";

    /**
     * Defines SNS Adapter
     */
    public static final String URL_STATUS_SNS_ADAPTER
            = "UrlStatusSNSAdapter";

    /**
     * Defines gson builder.
     */
    public static final String GSON
            = "gson";

    /**
     * Defines object mapper with default typing.
     */
    public static final String OBJECT_MAPPER_WITH_DEFAULT_TYPING
            = "ObjectMapperWithDefaultTyping";

    /**
     * Defines the dlq url for bi delta sync.
     */
    public static final String BI_DELTA_SYNC_DLQ_URL
            = "BIDeltaSyncDlqUrl";

    /**
     * Defines post url review action task factory.
     */
    public static final String POST_URL_REVIEW_ACTION_TASK_FACTORY = "PostUrlReviewActionTaskFactory";

    /**
     * Defines update and send notification task handler.
     */
    public static final String UPDATE_AND_SEND_NOTIFICATION_TASK = "UpdateAndSendNotificationTaskHandler";

    /**
     * Defines update and send notification in given range task handler.
     */
    public static final String UPDATE_AND_SEND_NOTIFICATION_IN_GIVEN_RANGE_TASK
            = "UpdateAndSendNotificationInGivenRangeTaskHandler";

    /**
     * Defines get all failed workflows task handler.
     */
    public static final String GET_ALL_FAILED_WORKFLOWS_TASK = "GetAllFailedWorkflowsTaskHandler";

    /**
     * Defines retry workflows task handler.
     */
    public static final String RETRY_WORKFLOWS_TASK = "RetryWorkflowsTaskHandler";

    /**
     * Defines start manual workflow task handler.
     */
    public static final String START_MANUAL_WORKFLOW_TASK  = "StartManualWorkflowTaskHandler";
}
