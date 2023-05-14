package com.amazon.amazonpaymerchanturl.module;

import amazon.platform.config.AppConfig;
import com.amazon.amazonpaymerchanturl.adapter.StegoServiceAdapter;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.utils.RetryStrategyUtil;
import com.amazon.cloudauth.client.CloudAuthCredentials;
import com.amazon.coral.client.CallAttachmentVisitor;
import com.amazon.coral.client.Calls;
import com.amazon.coral.client.cloudauth.CloudAuthDefaultCredentialsVisitor;
import com.amazon.coral.client.ClientBuilder;
import com.amazon.coral.google.common.collect.ImmutableList;
import com.amazon.identity.stego.DependencyException;
import com.amazon.identity.stego.StegoServiceClient;

import com.amazon.identity.stego.ThrottlingException;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.DELIMITER;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.REALM;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.STAGE;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.CLOUD_AUTH;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.STEGO_SVC_BASE;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.getHTTPClientConfigOverride;

@Module
public class StegoServiceModule {

    @Singleton
    @Provides
    public StegoServiceClient providesStegoServiceClient(final CloudAuthCredentials cloudAuthCredentials,
                                                         @Named(REALM) final String realm,
                                                         @Named(STAGE) final String stage) {
        final String qualifier = CLOUD_AUTH + DELIMITER + AppConfig.getDomain() + DELIMITER + realm;

        final String clientConfigOverride = getHTTPClientConfigOverride(STEGO_SVC_BASE, qualifier);
        final ClientBuilder clientBuilder =
                new ClientBuilder(new ByteArrayInputStream(clientConfigOverride.getBytes(StandardCharsets.UTF_8)));
        return clientBuilder.
                remoteOf(StegoServiceClient.class)
                .withConfiguration(qualifier)
                .withCallVisitors(new CloudAuthDefaultCredentialsVisitor(cloudAuthCredentials),
                        new CallAttachmentVisitor(Calls.retry(RetryStrategyUtil.getDefaultRetryStrategy(
                                ImmutableList.of(ThrottlingException.class, DependencyException.class)))))
                .newClient();
    }

    @Singleton
    @Provides
    public StegoServiceAdapter providesStegoServiceAdapter(
            final StegoServiceClient stegoServiceClient,
            final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        return new StegoServiceAdapter(stegoServiceClient, cloudWatchMetricsHelper);
    }
}
