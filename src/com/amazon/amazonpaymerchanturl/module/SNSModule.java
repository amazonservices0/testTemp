package com.amazon.amazonpaymerchanturl.module;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.AWS_DEFAULT_REGION;

import com.amazon.amazonpaymerchanturl.constants.ModuleConstants;
import com.amazon.lambdaskurge.adapter.SNSAdapter;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class SNSModule {

    private static final String URL_STATUS_SNS_ADAPTER = "UrlStatusSNSAdapter";
    private static final String URL_STATUS_SNS_CLIENT = "UrlStatusSNSClient";

    @Provides
    @Named(URL_STATUS_SNS_ADAPTER)
    public SNSAdapter providesSNSAdapter(@Named(URL_STATUS_SNS_CLIENT) final AmazonSNS amazonSNS) {
        return new SNSAdapter(amazonSNS);
    }

    @Singleton
    @Provides
    @Named(URL_STATUS_SNS_CLIENT)
    public AmazonSNS providesSNSClient() {
        return AmazonSNSClientBuilder
                .standard()
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .withRegion(System.getenv(AWS_DEFAULT_REGION))
                .build();
    }

    @Provides
    @Named(ModuleConstants.URL_STATUS_TOPIC)
    public String providesUrlStatusNotificationTopic() {
        return System.getenv(ModuleConstants.URL_STATUS_TOPIC);
    }
}
