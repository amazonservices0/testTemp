package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.constants.VendorUpdateType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLInvalidInputException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.UpdateVendorOnInvestigationRequest;
import com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.urlvendorreviewlib.component.DaggerUrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.component.UrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewClientException;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewServerException;
import com.amazon.urlvendorreviewlib.factory.VendorDeboardUrlHandlerFactory;
import com.amazon.urlvendorreviewlib.factory.VendorReviewHandlerFactory;
import com.amazon.urlvendorreviewmodel.request.VendorDeboardUrlRequest;
import com.amazon.urlvendorreviewmodel.request.VendorReviewRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.TREATMENT_T1;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EVERCOMPLIANT_ID;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants
        .UPDATE_VENDOR_ON_INVESTIGATION_SUCCESS_MESSAGE;

/**
 * UpdateVendorOnInvestigation update the vendor about investigation.
 */
@RequiredArgsConstructor
@Log4j2
public class UpdateVendorOnInvestigationHandler implements RequestStreamHandler {

    private final LambdaComponent lambdaComponent;
    private final JSONObjectMapperUtil jsonObjectMapperUtil;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final WeblabHelper weblabHelper;
    private final UrlVendorReviewLibComponent urlVendorReviewLibComponent;
    private final VendorDeboardUrlHandlerFactory vendorDeboardUrlHandlerFactory;
    private final VendorReviewHandlerFactory vendorReviewHandlerFactory;

    private static final String AUTO_DEBOARD_URL_DVS_WEBLAB_NAME = "AUTO_DEBOARD_URL_DVS_570258";

    public UpdateVendorOnInvestigationHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.jsonObjectMapperUtil = lambdaComponent.providesJSONObjectMapperUtil();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.domainValidationDDBAdapter = lambdaComponent.providesDomainValidationDDBAdapter();
        this.weblabHelper = lambdaComponent.provideWeblabHelper();
        urlVendorReviewLibComponent = DaggerUrlVendorReviewLibComponent.create();
        vendorDeboardUrlHandlerFactory = urlVendorReviewLibComponent.getVendorDeboardUrlHandlerFactory();
        vendorReviewHandlerFactory = urlVendorReviewLibComponent.getVendorReviewHandlerFactory();
    }

    /**
     * handleRequest entry point for updateVendorOnInvestigation Lambda to de-board/re-onboard url.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    @Override
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        final String lambdaName = Lambda.lambdaFunctionName(context);

        final UpdateVendorOnInvestigationRequest updateVendorOnInvestigationRequest
                = jsonObjectMapperUtil.deserialize(inputStream, UpdateVendorOnInvestigationRequest.class);

        log.info(lambdaName + " is called with input: {}", updateVendorOnInvestigationRequest);

        final String clientReferenceGroupId = updateVendorOnInvestigationRequest.getClientReferenceGroupId();
        final String merchantId = CustomInfoUtil.getMerchantId(clientReferenceGroupId);
        final String marketplaceId = CustomInfoUtil.getMarketplaceId(clientReferenceGroupId);
        final String treatment = weblabHelper.getWeblabTreatment(AUTO_DEBOARD_URL_DVS_WEBLAB_NAME,
                merchantId, marketplaceId);
        log.info("Weblab {} treatment is: {} for merchantId: {} and marketplaceId: {}",
                AUTO_DEBOARD_URL_DVS_WEBLAB_NAME, treatment, merchantId, marketplaceId);
        String responseMessage = UPDATE_VENDOR_ON_INVESTIGATION_SUCCESS_MESSAGE;
        if (TREATMENT_T1.equals(treatment)) {
            updateUrl(updateVendorOnInvestigationRequest);
        } else {
            responseMessage = "Weblab: " + AUTO_DEBOARD_URL_DVS_WEBLAB_NAME + " treatment is C for "
                    + "clientReferenceGroupId: " + clientReferenceGroupId + ", hence vendor update stopped for url: "
                    + updateVendorOnInvestigationRequest.getUrl();
        }
        sendLambdaResponse(outputStream, lambdaName, responseMessage);
    }

    private void updateUrl(final UpdateVendorOnInvestigationRequest updateVendorOnInvestigationRequest) {
        switch (VendorUpdateType.fromValue(updateVendorOnInvestigationRequest.getVendorUpdateType())) {
            case DEBOARD_URL:
                validateDomainValidationDDBEntry(updateVendorOnInvestigationRequest.getClientReferenceGroupId(),
                        updateVendorOnInvestigationRequest.getUrl());
                deboardUrl(updateVendorOnInvestigationRequest);
                break;

            case ONBOARD_URL:
                onboardUrl(updateVendorOnInvestigationRequest);
                break;

            default:
                throw new AmazonPayMerchantURLInvalidInputException("Invalid vendor update type: "
                        + updateVendorOnInvestigationRequest.getVendorUpdateType());
        }
    }

    private void deboardUrl(final UpdateVendorOnInvestigationRequest updateVendorOnInvestigationRequest) {
        final VendorDeboardUrlRequest vendorDeboardUrlRequest = VendorDeboardUrlRequest.builder()
                .clientReferenceGroupId(updateVendorOnInvestigationRequest.getClientReferenceGroupId())
                .reviewURL(updateVendorOnInvestigationRequest.getUrl())
                .subInvestigationType(updateVendorOnInvestigationRequest.getSubInvestigationType())
                .investigationId(updateVendorOnInvestigationRequest.getInvestigationId())
                .build();

        try {
            // TODO: for multiple vendor we have to remove the hardCode EverCompliant Id
            //  and we have to pass the vendor id.
            vendorDeboardUrlHandlerFactory.produceVendorHandler(EVERCOMPLIANT_ID)
                    .deboardUrl(vendorDeboardUrlRequest);
        } catch (UrlVendorReviewClientException e) {
            final String msg = String.format("Non-retryable exception received. while de-boarding with request: %s",
                    vendorDeboardUrlRequest);
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (UrlVendorReviewServerException e) {
            final String msg = String.format("Non-retryable exception received. while de-boarding with request: %s",
                    vendorDeboardUrlRequest);
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }
    }

    private void validateDomainValidationDDBEntry(final String clientReferenceGroupId, final String url) {

        final AmazonPayDomainValidationItem amazonPayDomainValidationItem;
        try {
            amazonPayDomainValidationItem = domainValidationDDBAdapter.loadEntry(clientReferenceGroupId, url);
        } catch (AmazonPayDomainValidationDAONonRetryableException e) {
            final String msg = String.format("Non-retryable exception occur. while fetching ddb entry for "
                    + "clientReferenceGroupId: %s and url: %s", clientReferenceGroupId, url);
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (AmazonPayDomainValidationDAORetryableException e) {
            final String msg = String.format("Retryable exception occur. while fetching ddb entry for "
                    + "clientReferenceGroupId: %s and url: %s", clientReferenceGroupId, url);
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }

        if (Objects.isNull(amazonPayDomainValidationItem)
                || CollectionUtils.isEmpty(amazonPayDomainValidationItem.getVariantURLs())) {
            throw new AmazonPayMerchantURLNonRetryableException("variant urls not merged for "
                    + "clientReferenceGroupId: " + clientReferenceGroupId);
        }
    }

    private void onboardUrl(final UpdateVendorOnInvestigationRequest updateVendorOnInvestigationRequest) {
        final VendorReviewRequest vendorReviewRequest = VendorReviewRequest.builder()
                .clientReferenceGroupId(updateVendorOnInvestigationRequest.getClientReferenceGroupId())
                .reviewURL(updateVendorOnInvestigationRequest.getUrl())
                .subInvestigationType(updateVendorOnInvestigationRequest.getSubInvestigationType())
                .build();

        try {
            // TODO: for multiple vendor we have to remove the hardCode EverCompliant Id
            //  and we have to pass the vendor id.
            vendorReviewHandlerFactory.produceVendorHandler(EVERCOMPLIANT_ID)
                    .initiateReview(vendorReviewRequest);
        } catch (UrlVendorReviewClientException e) {
            final String msg = String.format("Non-retryable exception received. while on-boarding with request: %s",
                    vendorReviewRequest);
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (UrlVendorReviewServerException e) {
            final String msg = String.format("Non-retryable exception received. while on-boarding with request: %s",
                    vendorReviewRequest);
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }
    }

    private void sendLambdaResponse(final OutputStream outputStream, final String lambdaName, final String response) {
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(SUCCESS_STATUS_CODE)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(response)
                .build());
    }
}
