package com.amazon.meridianservice.dagger.modules;

import amazon.platform.config.AppConfig;
import amazon.platform.config.AppConfigTree;
import com.amazon.meridianservice.constants.ModuleConstants;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class ConfigModule {
    private static final String CORAL_CONFIG = "/coral-config";
    private static final String LAMBDA_TASK_ROOT_KEY = "LAMBDA_TASK_ROOT";
    private static final String CORAL_CONFIG_PATH_KEY = "CORAL_CONFIG_PATH";
    private static final String MERIDIAN_SERVICE_APP_NAME = "MeridianService";

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
    public AppConfigTree providesAppConfig(@Named(ModuleConstants.REALM) final String realm,
                                           @Named(ModuleConstants.STAGE) final String stage) {
        if (!AppConfig.isInitialized()) {
            String lambdaTaskRoot = System.getenv(LAMBDA_TASK_ROOT_KEY);
            System.setProperty(CORAL_CONFIG_PATH_KEY, lambdaTaskRoot + CORAL_CONFIG);
            String[] appArgs = {"--root=" + lambdaTaskRoot, "--domain=" + stage, "--realm=" + realm};
            AppConfig.initialize(MERIDIAN_SERVICE_APP_NAME, null, appArgs);
        }
        return AppConfig.instance();
    }
}
