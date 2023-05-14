package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.URLReviewRequest;
import com.amazon.amazonpaymerchanturl.utils.HandlersUtil;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.urlvendorreviewlib.component.DaggerUrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.component.UrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewClientException;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewServerException;
import com.amazon.urlvendorreviewlib.factory.VendorReviewHandlerFactory;
import com.amazon.urlvendorreviewmodel.request.VendorReviewRequest;
import com.amazon.urlvendorreviewmodel.response.VendorReviewResponse;
import com.amazon.urlvendorreviewmodel.type.SubInvestigationStatus;
import com.amazonaws.services.lambda.runtime.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EVERCOMPLIANT_ID;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.INITIATE_VENDOR_REVIEW_SUCCESS_MESSAGE;

/**
 * InitiateVendorReviewHandler initiates VendorReview for a given URL.
 */
@RequiredArgsConstructor
@Log4j2
public class InitiateVendorReviewHandler {

    private final LambdaComponent lambdaComponent;
    private final JSONObjectMapperUtil jsonObjectMapperUtil;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final UrlVendorReviewLibComponent urlVendorReviewLibComponent;
    private final VendorReviewHandlerFactory vendorReviewHandlerFactory;

    public InitiateVendorReviewHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.jsonObjectMapperUtil = lambdaComponent.providesJSONObjectMapperUtil();
        this.urlInvestigationDDBAdapter = lambdaComponent.providesUrlInvestigationDDBAdapter();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        urlVendorReviewLibComponent = DaggerUrlVendorReviewLibComponent.create();
        vendorReviewHandlerFactory = urlVendorReviewLibComponent.getVendorReviewHandlerFactory();
    }

    /**
     * handleRequest entry point for InitiateVendorReview Lambda to initiate VendorReview
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        final String lambdaName = Lambda.lambdaFunctionName(context);
        log.info(lambdaName + " Lambda invoked");

        final URLReviewRequest urlReviewRequest
                = jsonObjectMapperUtil.deserialize(inputStream, URLReviewRequest.class);
        log.info("[LAMBDA_INPUT] {} is called with input: {}", lambdaName, urlReviewRequest);

        createUrlInvestigationDDBEntry(urlReviewRequest);

        final VendorReviewResponse vendorReviewResponse = initiateReview(urlReviewRequest);

        final String reviewInfo = jsonObjectMapperUtil.serialize(vendorReviewResponse.getReviewInfo());
        updateReviewInfoForUrlInvestigationDDBEntry(urlReviewRequest.getInvestigationId(),
                urlReviewRequest.getSubInvestigationType(), reviewInfo);

        sendLambdaResponse(outputStream, lambdaName, SUCCESS_STATUS_CODE, INITIATE_VENDOR_REVIEW_SUCCESS_MESSAGE);
    }

    private VendorReviewResponse initiateReview(final URLReviewRequest urlReviewRequest) {

        log.info("Call to EverCompliant initiated for clientReferenceGroupId: {} and  url: {}",
                urlReviewRequest.getClientReferenceGroupId(), urlReviewRequest.getReviewURL());
        final VendorReviewRequest vendorReviewRequest = getVendorReviewRequest(urlReviewRequest);

        try {
            // TODO: for multiple vendor we have to remove the hardCode EverCompliant Id
            //  and we have to pass the vendor id.
            return vendorReviewHandlerFactory
                    .produceVendorHandler(EVERCOMPLIANT_ID).initiateReview(vendorReviewRequest);
        } catch (UrlVendorReviewClientException e) {
            final String msg = "Non-retryable exception received. while initiating vendor review for clientRefGrpId: "
                    + urlReviewRequest.getClientReferenceGroupId() + " and url: " + urlReviewRequest.getReviewURL();
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (UrlVendorReviewServerException e) {
            final String msg = "Retryable exception received. while initiating vendor review for clientRefGrpId: "
                    + urlReviewRequest.getClientReferenceGroupId() + " and url: " + urlReviewRequest.getReviewURL();
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }

    }

    private void createUrlInvestigationDDBEntry(final URLReviewRequest urlReviewRequest) {
        final String clientRefGroupIdUrl = HandlersUtil
                .createClientReferenceGroupIdUrl(urlReviewRequest.getClientReferenceGroupId(),
                        urlReviewRequest.getReviewURL());
        long reviewStartTime = Instant.now().toEpochMilli();
        final UrlInvestigationItem urlInvestigationItem = UrlInvestigationItem.builder()
                .investigationId(urlReviewRequest.getInvestigationId())
                .investigationType(urlReviewRequest.getInvestigationType())
                .subInvestigationType(urlReviewRequest.getSubInvestigationType())
                .subInvestigationTaskToken(urlReviewRequest.getSubInvestigationTaskToken())
                .subInvestigationStatus(SubInvestigationStatus.IN_REVIEW.getSubInvestigationStatus())
                .clientReferenceGroupIdUrl(clientRefGroupIdUrl)
                .reviewStartTime(reviewStartTime)
                .reviewStartDate(Instant.ofEpochMilli(reviewStartTime).truncatedTo(ChronoUnit.DAYS).toEpochMilli())
                .build();
        createOrUpdateEntry(urlInvestigationItem);
    }

    private void updateReviewInfoForUrlInvestigationDDBEntry(final String investigationId,
                                                             final String subInvestigationType,
                                                             final String reviewInfo) {
        final UrlInvestigationItem urlInvestigationItem = UrlInvestigationItem.builder()
                .investigationId(investigationId)
                .subInvestigationType(subInvestigationType)
                .reviewInfo(reviewInfo)
                .build();
        createOrUpdateEntry(urlInvestigationItem);
    }

    private void createOrUpdateEntry(final UrlInvestigationItem urlInvestigationItem) {
        try {
            urlInvestigationDDBAdapter.createOrUpdateEntry(urlInvestigationItem);
        } catch (AmazonPayDomainValidationDAONonRetryableException | UrlVendorReviewClientException e) {
            final String msg = String.format("Non-retryable exception received. while saving url investigation "
                    + "ddb item: %s", urlInvestigationItem);
            throw new AmazonPayMerchantURLNonRetryableException(msg, e);
        } catch (AmazonPayDomainValidationDAORetryableException | UrlVendorReviewServerException e) {
            final String msg = String.format("Retryable exception received. while saving url investigation ddb "
                    + "item: %s", urlInvestigationItem);
            throw new AmazonPayMerchantURLRetryableException(msg, e);
        }
    }

    private VendorReviewRequest getVendorReviewRequest(final URLReviewRequest urlReviewRequest) {
        return VendorReviewRequest.builder()
                .clientReferenceGroupId(urlReviewRequest.getClientReferenceGroupId())
                .reviewURL(urlReviewRequest.getReviewURL())
                .subInvestigationType(urlReviewRequest.getSubInvestigationType())
                .build();
    }

    private void sendLambdaResponse(final OutputStream outputStream, final String lambdaName,
                                    final int statusCode, final String responseDetails) {
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(statusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(responseDetails)
                .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                .build());
    }
}
