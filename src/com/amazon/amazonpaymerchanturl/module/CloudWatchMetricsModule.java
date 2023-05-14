package com.amazon.amazonpaymerchanturl.module;

import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.lambdaskurge.adapter.MetricsAdapter;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.METRIC_NAMESPACE;


@Module
public class CloudWatchMetricsModule {

    @Singleton
    @Provides
    public CloudWatchMetricsHelper providesCloudWatchMetricsHelper(final MetricsAdapter metricsAdapter) {
        return new CloudWatchMetricsHelper(metricsAdapter);
    }

    @Singleton
    @Provides
    public MetricsAdapter providesMetricsAdaptor() {
        return new MetricsAdapter(providesAmazonCloudWatchClient(), providesMetricNamespace());
    }

    private AmazonCloudWatch providesAmazonCloudWatchClient() {
        return AmazonCloudWatchClientBuilder.standard().build();
    }

    private String providesMetricNamespace() {
        return METRIC_NAMESPACE;
    }
}
