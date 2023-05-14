package com.amazon.meridianservice.dagger.component;

import com.amazon.lambdadaggerlauncher.LambdaHandler;
import com.amazon.meridianservice.dagger.modules.ApplicationModule;
import com.amazon.meridianservice.dagger.modules.BusinessCreditDataServiceModule;
import com.amazon.meridianservice.dagger.modules.ClientModule;
import com.amazon.meridianservice.dagger.modules.S3Module;
import com.amazon.meridianservice.dagger.modules.DAOModule;
import com.amazon.meridianservice.dagger.modules.ConfigModule;
import com.amazon.meridianservice.handler.BCDSRequestInitHandler;
import com.amazon.meridianservice.handler.GetBatchMerchantRiskHandler;
import com.amazon.meridianservice.handler.InitiateOnboardingHandler;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component : This tells which class is the entry point for a lambda.
 */
@Component(modules = {
        ClientModule.class,
        S3Module.class,
        DAOModule.class,
        ApplicationModule.class,
        ConfigModule.class,
        S3Module.class,
        BusinessCreditDataServiceModule.class})
@Singleton
public interface LambdaComponent {

    @LambdaHandler
    GetBatchMerchantRiskHandler getBatchMerchantRiskHandler();

    @LambdaHandler
    InitiateOnboardingHandler initiateOnboardingHandler();

    @LambdaHandler
    BCDSRequestInitHandler bcdsRequestInitHandler();
}
