package com.amazon.amazonpaymerchanturl.module;

import amazon.platform.config.AppConfig;
import com.amazon.amazonpaymerchanturl.adapter.SlapshotAdapter;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazon.amazonpaymerchanturl.provider.WeblabTreatmentInformationProvider;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.amazonpaymerchanturl.utils.RetryStrategyUtil;
import com.amazon.cloudauth.client.CloudAuthCredentials;
import com.amazon.coral.client.CallAttachmentVisitor;
import com.amazon.coral.client.Calls;
import com.amazon.coral.client.ClientBuilder;
import com.amazon.coral.client.cloudauth.CloudAuthDefaultCredentialsVisitor;
import com.amazon.coral.google.common.collect.ImmutableList;
import com.amazon.coral.retry.RetryContext;
import com.amazon.coral.retry.RetryStrategy;
import com.amazon.paragoninvestigationservice.RetryableDependencyException;
import com.amazon.slapshot.coral.CSSlapshotServiceClient;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.CLOUD_AUTH;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.DELIMITER;

/**
 * Class to get {@link WeblabTreatmentInformationProvider} and {@link CSSlapshotServiceClient}.
 *
 */
@Module
public class SlapshotServiceModule {

    /**
     * Method to provide CSSlapshotServiceClient.
     * TODO create slapshot VPC endpoints in CDK Package - https://issues.amazon.com/issues/D32554909
     * @return CSSlapshotServiceClient.
     */
    @Provides
    @Singleton
    public  CSSlapshotServiceClient provideCSSlapshotServiceClient(final CloudAuthCredentials cloudAuthCredentials) {
        final String qualifier = AppConfig.getDomain() + DELIMITER + AppConfig.getRealm() + DELIMITER + CLOUD_AUTH;
        return new ClientBuilder()
                .remoteOf(CSSlapshotServiceClient.class)
                .withConfiguration(qualifier)
                .withCallVisitors(new CloudAuthDefaultCredentialsVisitor(cloudAuthCredentials),
                        new CallAttachmentVisitor(Calls.retry(RetryStrategyUtil.getDefaultRetryStrategy(
                                ImmutableList.of(RetryableDependencyException.class)))))
                .newClient();
    }

    /**
     * Method to provide SlapshotAdapter.
     * @return SlapshotAdapter.
     */
    @Provides
    @Singleton
    public SlapshotAdapter provideSlapshotAdapter(final CSSlapshotServiceClient csSlapshotServiceClient,
                                                  final JSONObjectMapperUtil jsonObjectMapperUtil) {
        return new SlapshotAdapter(csSlapshotServiceClient, jsonObjectMapperUtil);
    }

    /**
     * Method to provide WeblabTreatmentInformationProvider.
     * @param slapshotAdapter SlapshotAdapter Instance
     * @param cloudWatchMetricsHelper CloudwatchMetricsHElper Instance
     * @return WeblabTreatmentInformationProvider.
     */
    @Provides
    @Singleton
    public WeblabTreatmentInformationProvider provideWeblabTreatmentInformationProvider(
            final SlapshotAdapter slapshotAdapter,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        return new WeblabTreatmentInformationProvider(slapshotAdapter, cloudWatchMetricsHelper);
    }

    @Provides
    @Singleton
    public RetryStrategy<RetryContext> provideDefaultRetryStrategy() {
        return RetryStrategyUtil.getDefaultRetryStrategy();
    }

    @Provides
    @Singleton
    public WeblabHelper provideWeblabHelper(final WeblabTreatmentInformationProvider weblabProvider) {
        return new WeblabHelper(weblabProvider);
    }
}
