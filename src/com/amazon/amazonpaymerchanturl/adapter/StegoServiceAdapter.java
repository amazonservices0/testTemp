package com.amazon.amazonpaymerchanturl.adapter;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.model.StegoServiceUpdateRequest;
import com.amazon.identity.stego.CfApplicationUpdateIn;
import com.amazon.identity.stego.CfApplicationUpdateOut;
import com.amazon.identity.stego.CfProfileGetApplicationIn;
import com.amazon.identity.stego.CfProfileGetApplicationOut;
import com.amazon.identity.stego.StegoServiceClient;
import com.amazon.identity.stego.ThrottlingException;
import com.amazon.identity.stego.DependencyException;
import com.amazon.identity.stego.UnsupportedAccountTypeException;
import com.amazon.identity.stego.InvalidInputException;
import com.amazon.identity.stego.InputException;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.STEGO_SERVICE_GET_LWA_APP_ERROR_METRIC;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.STEGO_SERVICE_UPDATE_LWA_APP_ERROR_METRIC;

/**
 * StegoServiceAdapter provides APIs allowing Domain
 * Validation platform interacting with Stego Service.
 */
@Log4j2
public class StegoServiceAdapter {
    private final StegoServiceClient stegoServiceClient;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;

    @Inject
    public StegoServiceAdapter(@NonNull final StegoServiceClient stegoServiceClient,
                               @NonNull final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        this.stegoServiceClient = stegoServiceClient;
        this.cloudWatchMetricsHelper = cloudWatchMetricsHelper;
    }

    /**
     * Calls Stego Service to get LWA application based on client Id.
     *
     * @param clientId ID associated with each LWA Application's client.
     * @return cfProfileGetApplicationOut LWA Application
     */
    public CfProfileGetApplicationOut getApplicationFromClientId(@NonNull final String clientId) {
        log.info("Calling Stego Service to get LWA application based on client Id: {}", clientId);

        try {
            final CfProfileGetApplicationIn cfProfileGetApplicationIn = CfProfileGetApplicationIn
                    .builder()
                    .withProfileId(clientId)
                    .build();
            final CfProfileGetApplicationOut cfProfileGetApplicationOut = stegoServiceClient
                    .newCfProfileGetApplicationCall().call(cfProfileGetApplicationIn);
            log.info("LWA Application corresponding to clientId {} is: {}", clientId, cfProfileGetApplicationOut);
            return cfProfileGetApplicationOut;
        } catch (final Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    STEGO_SERVICE_GET_LWA_APP_ERROR_METRIC);
            throw handleException(e, "Error While Calling Stego Service to Get Application for clientId: " + clientId);
        }
    }

    /**
     * Calls Stego Service to update LWA application with origin URL.
     *
     * @param stegoServiceUpdateRequest This contains appId, appHash and appData.
     * @return CfApplicationUpdateOut LWA Application Update API Response.
     */
    public CfApplicationUpdateOut updateLWAApplication(
            @NonNull final StegoServiceUpdateRequest stegoServiceUpdateRequest) {
        log.info("Calling Stego Service to Update LWA application with input: {}",
                stegoServiceUpdateRequest);
        try {
            final CfApplicationUpdateIn cfApplicationUpdateIn = CfApplicationUpdateIn
                    .builder()
                    .withApplicationId(stegoServiceUpdateRequest.getAppId())
                    .withApplicationHash(stegoServiceUpdateRequest.getAppHash())
                    .withApplicationData(stegoServiceUpdateRequest.getApplicationData())
                    .build();

            final CfApplicationUpdateOut cfResponse = stegoServiceClient
                    .newCfApplicationUpdateCall()
                    .call(cfApplicationUpdateIn);
            return cfResponse;
        } catch (final Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    STEGO_SERVICE_UPDATE_LWA_APP_ERROR_METRIC);
            throw handleException(e, "Error While Calling Stego Service to update Application");
        }
    }

    private AmazonPayMerchantURLBaseException handleException(Exception e, String msg) {
        log.error("Exception Found: Message {} Error {}", msg, e);
        if (e instanceof ThrottlingException) {
            throw new AmazonPayMerchantURLRetryableException("Stego Service Throttling Exception: " + msg, e);
        } else if (e instanceof DependencyException) {
            throw new AmazonPayMerchantURLRetryableException("Stego Service Dependency Exception: " + msg, e);
        } else if (e instanceof InvalidInputException) {
            throw new AmazonPayMerchantURLNonRetryableException("Stego Service Invalid Input Exception: " + msg, e);
        } else if (e instanceof UnsupportedAccountTypeException) {
            throw new AmazonPayMerchantURLNonRetryableException("Stego Service Unsupported Account Type Exception: "
                    + msg, e);
        } else if (e instanceof InputException) {
            throw new AmazonPayMerchantURLNonRetryableException("Stego Service Input Type Exception: "
                    + msg, e);
        } else {
            throw new AmazonPayMerchantURLRetryableException("Stego Service Unknown Exception: " + msg, e);
        }
    }
}
