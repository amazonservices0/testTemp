package com.amazon.amazonpaymerchanturl.module;

import amazon.platform.config.AppConfig;
import com.amazon.cloudauth.client.CloudAuthCredentials;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class S3Module {

    @Provides
    @Singleton
    @Named("BUCKET_FOR_METRICS_REPORT")
    public String providesBucketForMetricsReport(final CloudAuthCredentials cloudAuthCredentials) {
        final String baseName = AppConfig.findString("AmazonPayMerchantURL.metrics.report.s3bucket");
        final String domain = getDomain(AppConfig.getDomain());
        final String realm = getRealm(AppConfig.getRealm().name());

        return String.format("%s-%s-%s", domain, realm, baseName);
    }

    @Provides
    @Singleton
    @Named("BUCKET_FOR_BI_DELTA_SYNC")
    public String providesBucketForBIDeltaSync() {
        final String baseName = AppConfig.findString(
                "AmazonPayMerchantURL.domain.validation.domainValidationTableS3Bucket");
        final String domain = getDomain(AppConfig.getDomain());
        final String realm = getRealm(AppConfig.getRealm().name());

        return String.format("%s-%s-%s", domain, realm, baseName);
    }

    private String getDomain(final String appConfigDomain) {

        switch (appConfigDomain) {
            case "test":
                return "beta";
            case "master":
                return "gamma";
            case "prod":
                return "prod";
            default:
                return appConfigDomain;
        }
    }

    private String getRealm(final String appConfigRealm) {

        switch (appConfigRealm) {
            case "USAmazon":
                return "na";
            case "FEAmazon":
                return "fe";
            case "EUAmazon":
                return "eu";
            default:
                return appConfigRealm;

        }
    }
}
