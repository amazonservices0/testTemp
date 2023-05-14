package com.amazon.meridianservice.dagger.modules;

import amazon.platform.config.AppConfigTree;
import com.amazon.meridianservice.adapters.S3Adapter;
import com.amazon.meridianservice.constants.ModuleConstants;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.amazon.meridianservice.constants.ModuleConstants.BCDS_REQUEST_S3_BUCKET;
import static com.amazon.meridianservice.constants.ModuleConstants.BCDS_RESPONSE_S3_BUCKET;

@Module
public class S3Module {
    @Singleton
    @Provides
    public AmazonS3 getS3Client() {
        return AmazonS3ClientBuilder.standard().build();
    }

    @Singleton
    @Provides
    public S3Adapter getS3Adapter(final AmazonS3 amazonS3, final ObjectMapper objectMapper) {
        return new S3Adapter(amazonS3, objectMapper);
    }

    @Provides
    @Singleton
    @Named(BCDS_REQUEST_S3_BUCKET)
    public String providesBCDSRequestBucketName(final AppConfigTree appConfig) {
        final String baseName = appConfig.findString("MeridianService.bcdsRequest.s3bucket");
        final String domain = getDomain(appConfig.getDomain());
        final String realm = getRealm(appConfig.getRealm().name());
        return String.format("%s-%s-%s", domain, realm, baseName);
    }

    @Provides
    @Singleton
    @Named(BCDS_RESPONSE_S3_BUCKET)
    public String providesBCDSResponseBucketName(final AppConfigTree appConfig) {
        final String baseName = appConfig.findString("MeridianService.bcdsResponse.s3bucket");
        final String domain = getDomain(appConfig.getDomain());
        final String realm = getRealm(appConfig.getRealm().name());
        return String.format("%s-%s-%s", domain, realm, baseName);
    }

    private String getDomain(String domain) {
        if (ModuleConstants.DOMAIN_TEST.equals(domain)) {
            return ModuleConstants.DOMAIN_BETA;
        } else if (ModuleConstants.DOMAIN_MASTER.equals(domain)) {
            return ModuleConstants.DOMAIN_GAMMA;
        } else if (ModuleConstants.DOMAIN_DEV.equals(domain)) {
            return ModuleConstants.DOMAIN_ALPHA;
        }
        return domain;
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
