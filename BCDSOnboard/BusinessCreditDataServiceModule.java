package com.amazon.meridianservice.dagger.modules;

import amazon.platform.config.AppConfigTree;
import com.amazon.businesscreditdataservice.BusinessCreditDataServiceLambdaClientFactory;
import com.amazon.meridianservice.constants.ModuleConstants;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.businesscreditdataservicelambda.BusinessCreditDataServiceLambda;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public class BusinessCreditDataServiceModule {

    @Provides
    @Singleton
    public BusinessCreditDataServiceLambda provideBcdsClient(AppConfigTree appConfig) {

        return BusinessCreditDataServiceLambdaClientFactory
                .builder()
                .credentials(getStsAssumeRoleSessionCredentialsProvider(appConfig))
                .domain(getDomain(appConfig))
                .region(getRealm(appConfig))
                .build()
                .getClient();
    }

    /**
     * Gets STS Assume Role credential provider, since the ddb we are trying to access is in another aws account.
     */
    private STSAssumeRoleSessionCredentialsProvider getStsAssumeRoleSessionCredentialsProvider(
            AppConfigTree appConfig) {
        String roleToAssume = appConfig.findString("BCDSAccessAssumeRole");
        return new STSAssumeRoleSessionCredentialsProvider.Builder(roleToAssume, "MeridianServiceAccess")
                .build();
    }

    private String getDomain(AppConfigTree appConfig) {
        String domain = appConfig.getDomain();
        if (ModuleConstants.DOMAIN_TEST.equals(domain)) {
            return ModuleConstants.DOMAIN_BETA;
        } else if (ModuleConstants.DOMAIN_MASTER.equals(domain)) {
            return ModuleConstants.DOMAIN_GAMMA;
        } else if (ModuleConstants.DOMAIN_DEV.equals(domain)) {
            return ModuleConstants.DOMAIN_ALPHA;
        }
        return domain;
    }

    //BCDS doesn't have eu prod qualifier, so directing EU prod to US prod. reference: https://tiny.amazon.com/104akomxa
    private String getRealm(AppConfigTree appConfig) {
        String domain = appConfig.getDomain();
        String realm = appConfig.getRealm().toString();
        if (ModuleConstants.REALM_EU.equals(realm) && ModuleConstants.DOMAIN_PROD.equals(domain)) {
            return ModuleConstants.REALM_NA;
        }
        return realm;
    }
}
