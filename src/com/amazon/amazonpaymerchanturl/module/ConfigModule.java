package com.amazon.amazonpaymerchanturl.module;

import amazon.platform.config.AppConfig;
import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StegoServiceAdapter;
import com.amazon.amazonpaymerchanturl.constants.ModuleConstants;
import com.amazon.amazonpaymerchanturl.processor.DeleteUrlProcessor;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.processor.UrlInvestigationMetricProcessor;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.AmazonPayMerchantURLAppConfig;
import com.amazon.amazonpaymerchanturl.processor.URLValidationResultProcessor;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.amazonpaymerchanturl.utils.StegoDBUrlUpdateUtil;
import com.amazon.amazonpaymerchanturl.utils.UrlStatusNotificationUtil;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.cloudcover.agent.CloudCoverJavaAgent;
import com.amazonaws.cloudcover.agent.CloudCoverJavaAgentBuilder;
import com.amazonaws.cloudcover.agent.CloudCoverJavaAgentNoOpImpl;
import com.amazonaws.cloudcover.agent.config.CloudCoverCoverageApplication;
import com.amazonaws.cloudcover.agent.config.CloudCoverCoverageGroup;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.amazon.cloudauth.client.CloudAuthCredentials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import java.nio.file.Paths;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.AWS_DEFAULT_REGION;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.BI_DELTA_SYNC_DLQ_URL;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_MANUAL_RESPONSE_WORKFLOW_DLQ_URL;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_URL_REVIEW_WORKFLOW_DLQ_URL;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.VENDOR_RESPONSE_VALIDATION_ERROR_QUEUE_URL;

@Module
public class ConfigModule {
    private static final String SSL_TRUSTSTORE_KEY = "javax.net.ssl.trustStore";
    private static final String SSL_TRUSTSTORE_PASSWORD_KEY = "javax.net.ssl.trustStorePassword";
    private static final String TRUSTSTORE_PASSWORD = "amazon";
    private static final String TRUSTSTORE = "truststore";
    private static final String TRUSTSTORE_FILE_NAME = "InternalAndExternalTrustStore.jks";
    private static final String SSL_TRUSTSTORE_TYPE_KEY = "javax.net.ssl.trustStoreType";
    private static final String TRUSTSTORE_TYPE_VALUE = "JKS";
    private static final String CORAL_CONFIG = "/coral-config";
    private static final String LAMBDA_TASK_ROOT_KEY = "LAMBDA_TASK_ROOT";
    private static final String CORAL_CONFIG_PATH_KEY = "CORAL_CONFIG_PATH";
    private static final String AMAZON_PAY_MERCHANT_URL_APP_NAME = "AmazonPayMerchantURL";
    private static final String URL_STATUS_SNS_ADAPTER = "UrlStatusSNSAdapter";
    private static final String PIPELINE_ID = "pipelineId";
    private static final String CLOUD_COVER_PACKAGE_LIST = "cloudcover.packageList";
    private static final String CLOUD_COVER_ENABLE = "cloudcover.DomainValidationEnable";

    @Singleton
    @Provides
    @Named(ModuleConstants.OBJECT_MAPPER)
    public ObjectMapper providesObjectMapper() {
        return new ObjectMapper();
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.GSON)
    public Gson providesGson() {
        return new GsonBuilder().disableHtmlEscaping().create();
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.OBJECT_MAPPER_WITH_DEFAULT_TYPING)
    public ObjectMapper providesObjectMapperWithDefaultTyping() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enableDefaultTyping(DefaultTyping.NON_FINAL);
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.REALM)
    public String providesRealm() {
        return System.getenv(ModuleConstants.REALM);
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.STAGE)
    public String providesStage() {
        return System.getenv(ModuleConstants.STAGE);
    }

    @Provides
    @Singleton
    public static CloudAuthCredentials getCloudAuthCredentials() {
        return new CloudAuthCredentials.RegionalAwsCredentials(DefaultAWSCredentialsProviderChain.getInstance(),
                System.getenv(AWS_DEFAULT_REGION));
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.QUEUE_ID_MAP)
    public Map<String, String> providesQueueIDMap() {
        Map<String, String> queueIdMap =
                AppConfig.findMap("AmazonPayMerchantURL.queueIdMap");
        return Optional.ofNullable(queueIdMap).orElseGet(Collections::emptyMap);
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.QUEUE_ID_TO_RISK_LEVEL_MAP)
    public Map<String, Map<String, String>> providesQueueIDToRiskLevelMap() {
        Map<String, Map<String, String>> queueIdMap =
                AppConfig.findMap("AmazonPayMerchantURL.queueIdToRiskLevelMap");
        return Optional.ofNullable(queueIdMap).orElseGet(Collections::emptyMap);
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.QUEUE_ID_FOR_PERIODIC_REVIEW_TO_RISK_LEVEL_MAP)
    public Map<String, Map<String, String>> providesQueueIDForPeriodicReviewToRiskLevelMap() {
        Map<String, Map<String, String>> queueIdMap =
                AppConfig.findMap("AmazonPayMerchantURL.queueIdForPeriodicReviewToRiskLevelMap");
        return Optional.ofNullable(queueIdMap).orElseGet(Collections::emptyMap);
    }

    @Singleton
    @Provides
    @Named(ModuleConstants.URL_REVIEW_WORKFLOW_MAP)
    public Map<String, String> providesUrlReviewWorkflowMap() {
        Map<String, String> urlReviewWorkflowMap = Optional.ofNullable(
                AppConfig.findMap("AmazonPayMerchantURL.urlReviewWorkflowMap"))
                .orElseGet(Collections::emptyMap);

        // Update Workflow ARN for each workflowType
        urlReviewWorkflowMap.forEach((key, workflowType) -> urlReviewWorkflowMap.put(key, System.getenv(workflowType)));
        return urlReviewWorkflowMap;
    }

    @Provides
    @Singleton
    @Named("AppConfig")
    public AmazonPayMerchantURLAppConfig providesAppConfig(@Named(ModuleConstants.REALM) final String realm,
            @Named(ModuleConstants.STAGE) final String stage) {
        if (!AppConfig.isInitialized()) {
            String lambdaTaskRoot = System.getenv(LAMBDA_TASK_ROOT_KEY);
            System.setProperty(CORAL_CONFIG_PATH_KEY, lambdaTaskRoot + CORAL_CONFIG);
            String certPath = Paths.get(lambdaTaskRoot, TRUSTSTORE, TRUSTSTORE_FILE_NAME).toString();
            System.setProperty(SSL_TRUSTSTORE_KEY, certPath);
            System.setProperty(SSL_TRUSTSTORE_PASSWORD_KEY, TRUSTSTORE_PASSWORD);
            System.setProperty(SSL_TRUSTSTORE_TYPE_KEY, TRUSTSTORE_TYPE_VALUE);
            String[] appArgs = {"--root=" + lambdaTaskRoot, "--domain=" + stage, "--realm=" + realm};
            AppConfig.initialize(AMAZON_PAY_MERCHANT_URL_APP_NAME, null, appArgs);
        }
        return new AmazonPayMerchantURLAppConfig();
    }

    @Singleton
    @Provides
    public StegoDBUrlUpdateUtil provideStegoDBUrlUpdateUtil(final StegoServiceAdapter stegoServiceAdapter) {
        return new StegoDBUrlUpdateUtil(stegoServiceAdapter);
    }

    @Singleton
    @Provides
    public UrlStatusNotificationUtil providesUrlStatusNotificationUtil(
            @Named(URL_STATUS_SNS_ADAPTER) final SNSAdapter snsAdapter,
            @Named(ModuleConstants.OBJECT_MAPPER) final ObjectMapper objectMapper,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        return new UrlStatusNotificationUtil(snsAdapter, objectMapper, cloudWatchMetricsHelper);
    }

    @Singleton
    @Provides
    public LambdaResponseUtil provideLambdaResponseUtil(final CloudWatchMetricsHelper cloudWatchMetricsHelper,
                                                        @Named(ModuleConstants.OBJECT_MAPPER)
                                                                ObjectMapper objectMapper) {
        return new LambdaResponseUtil(cloudWatchMetricsHelper, objectMapper);
    }

    @Singleton
    @Provides
    public JSONObjectMapperUtil providesJSONObjectMapperUtil(@Named(ModuleConstants.OBJECT_MAPPER)
                                                                 final ObjectMapper objectMapper) {
        return new JSONObjectMapperUtil(objectMapper);
    }

    @Singleton
    @Provides
    public URLValidationResultProcessor provideURLValidationResultProcessor(
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            final StegoDBUrlUpdateUtil stegoDBUrlUpdateUtil,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper,
            final UrlStatusNotificationUtil urlStatusNotificationUtil,
            @Named(ModuleConstants.URL_STATUS_TOPIC) final String urlNotificationTopic) {
        return new URLValidationResultProcessor(domainValidationDDBAdapter, urlInvestigationDDBAdapter,
                stegoDBUrlUpdateUtil, cloudWatchMetricsHelper, urlStatusNotificationUtil, urlNotificationTopic);
    }

    @Singleton
    @Provides
    public UrlInvestigationMetricProcessor provideUrlInvestigationMetricProcessor(
            final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
            final Clock systemClock) {
        return new UrlInvestigationMetricProcessor(urlInvestigationDDBAdapter, systemClock);
    }

    @Singleton
    @Provides
    @Named(VENDOR_RESPONSE_VALIDATION_ERROR_QUEUE_URL)
    public String providesVendorResponseValidationErrorQueueUrl() {
        return System.getenv(VENDOR_RESPONSE_VALIDATION_ERROR_QUEUE_URL);
    }

    @Provides
    @Singleton
    public CloudCoverJavaAgent provideCloudCoverJavaAgent() {
        if (!AppConfig.findBoolean(CLOUD_COVER_ENABLE)) {
            return new CloudCoverJavaAgentNoOpImpl();
        }
        final CloudCoverJavaAgentBuilder builder = new CloudCoverJavaAgentBuilder()
                .withCloudCoverCoverageGroup(
                        new CloudCoverCoverageGroup()
                                .withName(AppConfig.getApplicationName())
                                .withAwsRegion(System.getenv(AWS_DEFAULT_REGION))
                                .withExternalId(AppConfig.findString(PIPELINE_ID))
                )
                .withCloudCoverCoverageApplication(
                        new CloudCoverCoverageApplication()
                                .withApplicationName(AppConfig.getApplicationName())
                                .withApplicationStage(AppConfig.getDomain())
                                .withPackages((String[]) AppConfig.findVector(CLOUD_COVER_PACKAGE_LIST)
                                        .toArray(new String[0]))
                );
        return builder.build();
    }

    @Singleton
    @Provides
    @Named(EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL)
    public String providesExecuteVendorResponseWorkflowErrorQueueUrl() {
        return System.getenv(EXECUTE_VENDOR_RESPONSE_WORKFLOW_ERROR_QUEUE_URL);
    }

    @Singleton
    @Provides
    @Named(EXECUTE_URL_REVIEW_WORKFLOW_DLQ_URL)
    public String providesExecuteUrlReviewWorkflowDlqUrl() {
        return System.getenv(EXECUTE_URL_REVIEW_WORKFLOW_DLQ_URL);
    }

    @Singleton
    @Provides
    @Named(EXECUTE_MANUAL_RESPONSE_WORKFLOW_DLQ_URL)
    public String providesExecuteManualResponseWorkflowDlqUrl() {
        return System.getenv(EXECUTE_MANUAL_RESPONSE_WORKFLOW_DLQ_URL);
    }

    @Singleton
    @Provides
    public Clock provideSystemClock() {
        return Clock.system(ZoneOffset.UTC);
    }

    @Singleton
    @Provides
    @Named(BI_DELTA_SYNC_DLQ_URL)
    public String providesBIDeltaSyncDlqUrl() {
        return System.getenv(BI_DELTA_SYNC_DLQ_URL);
    }

    @Singleton
    @Provides
    public DeleteUrlProcessor providesDeleteUrlProcessor(
            final DomainValidationDDBAdapter domainValidationDDBAdapter,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper,
            final UrlStatusNotificationUtil urlStatusNotificationUtil,
            @Named(ModuleConstants.URL_STATUS_TOPIC) final String urlNotificationTopic) {
        return new DeleteUrlProcessor(domainValidationDDBAdapter, cloudWatchMetricsHelper,
                urlStatusNotificationUtil, urlNotificationTopic);
    }
}
