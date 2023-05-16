package com.amazon.amazonpaymerchanturl.component;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.module.DynamoDBMapperModule;
import com.amazon.amazonpaymerchanturl.adapter.ParagonInvestigationServiceAdapter;
import com.amazon.amazonpaymerchanturl.adapter.S3Adapter;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StegoServiceAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.constants.ModuleConstants;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.AmazonPayMerchantURLAppConfig;
import com.amazon.amazonpaymerchanturl.module.AWSServicesClientModule;
import com.amazon.amazonpaymerchanturl.module.AWSAdapterModule;
import com.amazon.amazonpaymerchanturl.module.ConfigModule;
import com.amazon.amazonpaymerchanturl.module.CloudWatchMetricsModule;
import com.amazon.amazonpaymerchanturl.module.ParagonServiceInvestigationModule;
import com.amazon.amazonpaymerchanturl.module.SNSModule;
import com.amazon.amazonpaymerchanturl.module.SlapshotServiceModule;
import com.amazon.amazonpaymerchanturl.module.StegoServiceModule;
import com.amazon.amazonpaymerchanturl.module.TranslatorModule;
import com.amazon.amazonpaymerchanturl.module.TaskModule;
import com.amazon.amazonpaymerchanturl.processor.URLValidationResultProcessor;
import com.amazon.amazonpaymerchanturl.provider.WeblabTreatmentInformationProvider;
import com.amazon.amazonpaymerchanturl.task.CallbackWorkflowDeterminatorTask;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.amazonpaymerchanturl.utils.UrlStatusNotificationUtil;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazon.amazonpaymerchanturl.utils.StegoDBUrlUpdateUtil;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.cloudcover.agent.CloudCoverJavaAgent;
import dagger.Component;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_URL_REVIEW_WORKFLOW_DLQ_ADAPTER;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL;

@Singleton
@Component(modules = {
        ConfigModule.class,
        ParagonServiceInvestigationModule.class,
        StegoServiceModule.class,
        DynamoDBMapperModule.class,
        CloudWatchMetricsModule.class,
        AWSServicesClientModule.class,
        SNSModule.class,
        AWSAdapterModule.class,
        TranslatorModule.class,
        TaskModule.class,
        SlapshotServiceModule.class
})
public interface LambdaComponent {

    @Named("AppConfig")
    AmazonPayMerchantURLAppConfig initializeAppConfig();

    @Named(ModuleConstants.QUEUE_ID_MAP)
    Map<String, String> providesQueueIDMap();

    @Named(ModuleConstants.QUEUE_ID_TO_RISK_LEVEL_MAP)
    Map<String, Map<String, String>> providesQueueIDToRiskLevelMap();

    @Named(ModuleConstants.QUEUE_ID_FOR_PERIODIC_REVIEW_TO_RISK_LEVEL_MAP)
    Map<String, Map<String, String>> providesQueueIDForPeriodicReviewToRiskLevelMap();

    @Named(ModuleConstants.URL_REVIEW_WORKFLOW_MAP)
    Map<String, String> providesUrlReviewWorkflowMap();

    @Named(ModuleConstants.URL_STATUS_TOPIC)
    String providesUrlStatusNotificationTopic();

    StegoServiceAdapter providesStegoServiceAdapter();

    ParagonInvestigationServiceAdapter getParagonInvestigationServiceAdapter();

    ObjectMapper providesObjectMapper();

    DomainValidationDDBAdapter providesDomainValidationDDBAdapter();

    UrlInvestigationDDBAdapter providesUrlInvestigationDDBAdapter();

    CloudWatchMetricsHelper providesCloudWatchMetricsHelper();

    AWSLambda providesLambdaServiceClient();

    AWSStepFunctions providesStepFunctionClient();

    AmazonSQS providesSQSServiceClient();

    @Named("UrlStatusSNSAdapter")
    SNSAdapter providesSNSAdapter();

    StegoDBUrlUpdateUtil provideStegoDBUrlUpdateUtil();

    LambdaResponseUtil provideLambdaResponseUtil();

    URLValidationResultProcessor provideURLValidationResultProcessor();

    @Named("VendorResponseErrorQueueAdapter")
    SQSAdapter providesVendorResponseErrorQueueAdapter();

    @Named(EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL)
    String providesExecuteVendorResponseWorkflowErrorQueueUrl();

    S3Adapter providesS3Adapter();

    CallbackWorkflowDeterminatorTask providesCallbackWorkflowDeterminatorTask();

    StepFunctionAdapter providesStepFunctionAdapter();

    JSONObjectMapperUtil providesJSONObjectMapperUtil();

    UrlStatusNotificationUtil providesUrlStatusNotificationUtil();

    CloudCoverJavaAgent provideCloudCoverJavaAgent();

    @Named(EXECUTE_URL_REVIEW_WORKFLOW_DLQ_ADAPTER)
    SQSAdapter providesExecuteUrlReviewWorkflowDlqAdapter();

    WeblabTreatmentInformationProvider provideWeblabTreatmentInformationProvider();
}
