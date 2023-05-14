package com.amazon.amazonpaymerchanturl.module;

import amazon.platform.config.AppConfig;
import com.amazon.amazonpaymerchanturl.adapter.ParagonInvestigationServiceAdapter;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.utils.RetryStrategyUtil;
import com.amazon.coral.client.CallAttachmentVisitor;
import com.amazon.coral.client.Calls;
import com.amazon.coral.client.ClientBuilder;
import com.amazon.coral.google.common.collect.ImmutableList;
import com.amazon.paragoninvestigationservice.ParagonInvestigationServiceClient;
import com.amazon.cloudauth.client.CloudAuthCredentials;
import com.amazon.coral.client.cloudauth.CloudAuthDefaultCredentialsVisitor;
import com.amazon.paragoninvestigationservice.RetryableDependencyException;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.DELIMITER;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.PARAGON_SVC_BASE;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.REALM;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.STAGE;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.CLOUD_AUTH;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.getHTTPClientConfigOverride;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.BETA_STAGE;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.US_AMAZON;

@Module
public class ParagonServiceInvestigationModule {


    @Singleton
    @Provides
    public ParagonInvestigationServiceClient getParagonInvestigationServiceClient(
            final CloudAuthCredentials cloudAuthCredentials,
            @Named(REALM) final String realm,
            @Named(STAGE) final String stage) {
        final String qualifier;

        // For Beta we using the CloudAuth.test.USAmazon because no config present for EU and FE in the file:
        // https://tiny.amazon.com/8utomuf8/codeamazpackParablob88d1cora
        //TODO move the qualifier logic to app Config.
        if (BETA_STAGE.equals(stage)) {
            qualifier = CLOUD_AUTH + DELIMITER + AppConfig.getDomain() + DELIMITER + US_AMAZON;
        } else {
            qualifier = CLOUD_AUTH + DELIMITER + AppConfig.getDomain() + DELIMITER + realm;
        }
        final String clientConfigOverride = getHTTPClientConfigOverride(PARAGON_SVC_BASE, qualifier);
        final ClientBuilder clientBuilder =
                new ClientBuilder(new ByteArrayInputStream(clientConfigOverride.getBytes(StandardCharsets.UTF_8)));
        return clientBuilder.
                remoteOf(ParagonInvestigationServiceClient.class)
                .withConfiguration(qualifier)
                .withCallVisitors(new CloudAuthDefaultCredentialsVisitor(cloudAuthCredentials),
                        new CallAttachmentVisitor(Calls.retry(RetryStrategyUtil.getDefaultRetryStrategy(
                                ImmutableList.of(RetryableDependencyException.class)))))
                .newClient();
    }

    @Singleton
    @Provides
    public ParagonInvestigationServiceAdapter getParagonInvestigationServiceAdapter(
            final ParagonInvestigationServiceClient paragonInvestigationServiceClient,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        return new ParagonInvestigationServiceAdapter(paragonInvestigationServiceClient, cloudWatchMetricsHelper);
    }
}
