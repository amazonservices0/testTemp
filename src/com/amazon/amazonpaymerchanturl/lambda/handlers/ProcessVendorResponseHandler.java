package com.amazon.amazonpaymerchanturl.lambda.handlers;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.AP_WEBSITE_REVIEW_US_WEBLAB;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.LOW_RISKLEVEL_LIST;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.TREATMENT_T1;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PROCESS_VENDOR_RESPONSE_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EVERCOMPLIANT_ID;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getMarketplaceId;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getMerchantId;

import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.UrlVendorReviewResponseInput;
import com.amazon.amazonpaymerchanturl.model.UrlVendorReviewResponseOutput;
import com.amazon.amazonpaymerchanturl.provider.WeblabTreatmentInformationProvider;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.urlvendorreviewlib.component.DaggerUrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.component.UrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewClientException;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewServerException;
import com.amazon.urlvendorreviewlib.factory.VendorProcessCallbackHandlerFactory;
import com.amazon.urlvendorreviewmodel.model.ScanSpec;
import com.amazon.urlvendorreviewmodel.model.UrlSpec;
import com.amazon.urlvendorreviewmodel.response.UrlVendorReviewCallbackResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Objects;

/**
 * ProcessVendorResponseHandler processes response received from VendorReview of a given URL.
 */
@RequiredArgsConstructor
@Builder
@Log4j2
public class ProcessVendorResponseHandler implements RequestStreamHandler {
    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final UrlVendorReviewLibComponent urlVendorReviewLibComponent;
    private final VendorProcessCallbackHandlerFactory vendorProcessCallbackHandlerFactory;
    private final WeblabTreatmentInformationProvider weblabProvider;

    public ProcessVendorResponseHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        urlVendorReviewLibComponent = DaggerUrlVendorReviewLibComponent.create();
        vendorProcessCallbackHandlerFactory = urlVendorReviewLibComponent.getVendorProcessCallbackHandlerfactory();
        this.weblabProvider = lambdaComponent.provideWeblabTreatmentInformationProvider();
    }

    /**
     * handleRequest entry point for ProcessVendorResponse Lambda to process response of VendorReview.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    public void handleRequest(@NonNull final InputStream inputStream, @NonNull final OutputStream outputStream,
                              @NonNull final Context context) {
        final String lambdaFunctionName = lambdaFunctionName(context);
        log.info(lambdaFunctionName + " lambda invoked.");

        UrlVendorReviewResponseInput urlVendorReviewResponseInput;
        try {
            urlVendorReviewResponseInput = objectMapper.readValue(inputStream, UrlVendorReviewResponseInput.class);
        } catch (Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_VENDOR_RESPONSE_FAILURE_METRICS);
            String errorMsg = "Error parsing inputStream to UrlVendorReviewResponse";
            log.info(errorMsg, e);
            throw new AmazonPayMerchantURLNonRetryableException(errorMsg, e);
        }

        final String investigationId = urlVendorReviewResponseInput.getInvestigationId();
        final ScanSpec scanSpec = urlVendorReviewResponseInput.getScanSpec();
        final String reviewUrl = urlVendorReviewResponseInput.getReviewURL();

        if (Objects.isNull(investigationId) || Objects.isNull(scanSpec) || Objects.isNull(reviewUrl)) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_VENDOR_RESPONSE_FAILURE_METRICS);
            log.info("InvestigationId/ScanSpec/ReviewUrl is null. InvestigationId {}, ScanSpec {}, ReviewUrl {}",
                    investigationId, scanSpec, reviewUrl);
            throw new AmazonPayMerchantURLNonRetryableException(
                    "error in processVendorResponse, InvestigationId/ScanSpec/ReviewUrl is null.");
        }

        UrlVendorReviewCallbackResponse urlVendorReviewCallbackResponse;
        try {
            urlVendorReviewCallbackResponse = vendorProcessCallbackHandlerFactory.produceVendorHandler(EVERCOMPLIANT_ID)
                    .processCallback(investigationId, scanSpec, reviewUrl);
        } catch (UrlVendorReviewClientException e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_VENDOR_RESPONSE_FAILURE_METRICS);
            String errorMsg = "NonRetryableException while processing VendorResponse for ClientReferenceGroupId: "
                    + urlVendorReviewResponseInput.getClientReferenceGroupId() + " and URL: " + reviewUrl;
            log.info(errorMsg, e);
            throw new AmazonPayMerchantURLNonRetryableException(errorMsg, e);
        } catch (UrlVendorReviewServerException e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_VENDOR_RESPONSE_FAILURE_METRICS);
            String errorMsg = "RetryableException while processing VendorResponse for ClientReferenceGroupId: "
                    + urlVendorReviewResponseInput.getClientReferenceGroupId() + " and URL: " + reviewUrl;
            log.info(errorMsg, e);
            throw new AmazonPayMerchantURLRetryableException(errorMsg, e);
        }

        final UrlVendorReviewResponseOutput urlVendorReviewResponseOutput =
                buildUrlVendorReviewResponseOutput(urlVendorReviewCallbackResponse, scanSpec);

        /*
        Note : MetricFilter syntax pattern should be updated in CDK package accordingly
        if there is a change in Log message
        */
        log.info("[PROCESSED_VENDOR_RESPONSE] Url status is {} after processing vendor response with risk level {} " +
                        "for clientRefGrpId: {}, url: {} and SubInvType {}",
                urlVendorReviewResponseOutput.getSubInvestigationStatus(),
                urlVendorReviewResponseOutput.getRiskLevel(),
                urlVendorReviewResponseInput.getClientReferenceGroupId(),
                reviewUrl, urlVendorReviewResponseOutput.getSubInvestigationType());

        lambdaResponseUtil.sendResponseJson(getResponseInput(SUCCESS_STATUS_CODE, outputStream,
                lambdaFunctionName, urlVendorReviewResponseOutput));
    }

    private LambdaResponseInput getResponseInput(final int responseStatusCode, final OutputStream outputStream,
                                                 final String lambdaName, final Object response) {
        return LambdaResponseInput.builder()
                .statusCode(responseStatusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(response)
                .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                .mapper(objectMapper)
                .build();
    }

    private UrlVendorReviewResponseOutput buildUrlVendorReviewResponseOutput(
            final UrlVendorReviewCallbackResponse urlVendorReviewCallbackResponse,
            final ScanSpec scanSpec) {
        final String scanId = scanSpec.getScanItems().keySet().stream().findFirst().get();
        final UrlSpec urlSpec = scanSpec.getScanItems().get(scanId);
        final String riskLevel = urlSpec.getRiskSpec().getRiskLevel().getRiskLevel();

        return UrlVendorReviewResponseOutput.builder()
                .subInvestigationStatus(urlVendorReviewCallbackResponse.getSubInvestigationStatus())
                .subInvestigationType(scanSpec.getScanType().getSubInvestigationType())
                .isManualReviewRequired(isWeblabDialedUpForLowRisk(scanSpec.getClientId(), riskLevel)
                        && urlVendorReviewCallbackResponse.isManualReviewRequired())
                .isOffline(urlVendorReviewCallbackResponse.isOffline())
                .isPasswordProtected(urlVendorReviewCallbackResponse.isPasswordProtected())
                .riskLevel(riskLevel)
                .reviewTime(Instant.now().toEpochMilli())
                .build();
    }

    /**
     * Returns true if weblab treatment T1
     * @return boolean - true or false
     */
    private boolean isWeblabDialedUpForLowRisk(final String clientRefGrpId,
                                               final String risklevel) {
        // No need to check treatment if risklevel is not No/Low risk.
        if (!LOW_RISKLEVEL_LIST.contains(risklevel)) {
            return true;
        }
        final String merchantId = getMerchantId(clientRefGrpId);
        log.info("Calling weblab provider to get treatment for Weblab: {}, MerchantId : {}",
                AP_WEBSITE_REVIEW_US_WEBLAB, merchantId);
        final String marketPlaceID = getMarketplaceId(clientRefGrpId);
        final String treatment = weblabProvider.getWebLabTreatmentInformation(merchantId,
                marketPlaceID, AP_WEBSITE_REVIEW_US_WEBLAB);
        return TREATMENT_T1.equals(treatment);
    }
}
