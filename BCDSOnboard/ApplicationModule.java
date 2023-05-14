package com.amazon.meridianservice.dagger.modules;

import com.amazon.meridianservice.adapters.BusinessCreditDataServiceAdapter;
import com.amazonaws.services.businesscreditdataservicelambda.BusinessCreditDataServiceLambda;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.amazon.meridianservice.constants.ModuleConstants.BCDS_RESPONSE_S3_BUCKET;

@Module
public class ApplicationModule {

    @Singleton
    @Provides
    public BusinessCreditDataServiceAdapter providesBusinessCreditDataServiceAdapter(
            final BusinessCreditDataServiceLambda businessCreditDataServiceLambda,
            @Named(BCDS_RESPONSE_S3_BUCKET) final String bcdsResponseS3Bucket) {
        return new BusinessCreditDataServiceAdapter(businessCreditDataServiceLambda, bcdsResponseS3Bucket);
    }

}
