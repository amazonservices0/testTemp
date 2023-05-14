package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;

import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.URLReviewRequest;
import com.amazon.amazonpaymerchanturl.model.URLReviewResponse;
import com.amazon.amazonpaymerchanturl.processor.URLValidationResultProcessor;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;

import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;

import com.amazon.apurlvalidation.UrlValidation;
import com.amazon.urlvalidationdaggerclientconfig.component.DaggerURLValidationComponent;
import com.amazon.apurlvalidation.model.UrlResponse;
import com.amazonaws.services.lambda.runtime.Context;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.Builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;

/**
 * StaticAllowedDeniedUrlValidateHandler checks the provided url in the request param are in allowed Urls or denied
 * Urls list, and take a corresponding action against those URLs.
 */

@Builder
@RequiredArgsConstructor
@Log4j2
public class UpfrontURLValidationHandler {

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper mapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final UrlValidation urlValidation;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final URLValidationResultProcessor urlValidationResultProcessor;

    private static final String AMAZON_PAY_BUSINESS = "AmazonPay";
    private static final String AUTO_APPROVED = "AutoApproved";
    private static final String AUTO_DENIED = "AutoDenied";

    public UpfrontURLValidationHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.mapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.urlValidation = DaggerURLValidationComponent.create().getUrlValidation();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.urlValidationResultProcessor = lambdaComponent.provideURLValidationResultProcessor();
    }

    /**
     * StaticAllowedDeniedUrlValidate will check whether Url is allowed and denied list auto approve the Url and
     * takes the
     * corresponding action.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
            final Context context) {
        final String lambdaName = lambdaFunctionName(context);
        final URLReviewRequest urlReviewRequest = deSerializeInputStream(inputStream, lambdaName, outputStream);
        if (Objects.isNull(urlReviewRequest)) {
            return;
        }
        log.info(lambdaName + " is called for ClientReferenceGroupId: {}, URL: {}",
                urlReviewRequest.getClientReferenceGroupId(), urlReviewRequest.getReviewURL());
        final long reviewStartTime = Instant.now().toEpochMilli();

        //TODO: Added hardcode string AmazonPay, will be removed when source of triggerUrlReviewRequest change.
        final List<UrlResponse> urlStatusResponseList = urlValidation.validateUrls(
                Collections.singletonList(urlReviewRequest.getReviewURL()), AMAZON_PAY_BUSINESS);
        final UrlResponse urlStatusResponse = urlStatusResponseList.get(0); // Single response as we are sending 1 url

        try {
            final URLReviewResponse response = urlValidationResultProcessor
                    .process(urlStatusResponse, urlReviewRequest, reviewStartTime);
            log.info("Url status after upfront Validation: {}", response.getSubInvestigationStatus());
            lambdaResponseUtil.sendResponseJson(getResponseInput(SUCCESS_STATUS_CODE, outputStream,
                    lambdaName, response));
        } catch (Exception e) {
            log.info("Error processing upfrontValidation result for {}", urlReviewRequest.getReviewURL(), e);
            lambdaResponseUtil.sendResponseJson(getResponseInput(FAILED_STATUS_CODE, outputStream, lambdaName,
                    "error in upfrontValidation"));
        }
    }

    private URLReviewRequest deSerializeInputStream(InputStream inputStream, String lambdaName,
            OutputStream outputStream) {
        try {
            return mapper.readValue(inputStream, URLReviewRequest.class);
        } catch (Exception exception) {
            log.info("Error while parsing the inputStream to triggerURLReviewRequest for lambda {}, exception: {}",
                    lambdaName, exception);
            lambdaResponseUtil.sendResponseJson(getResponseInput(FAILED_STATUS_CODE, outputStream, lambdaName,
                    "input parsing failed"));
            return null;
        }
    }

    private LambdaResponseInput getResponseInput(final int responseStatusCode,
            final OutputStream outputStream, final String lambdaName, final Object response) {
        return LambdaResponseInput.builder()
                .statusCode(responseStatusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(response)
                .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                .mapper(this.mapper)
                .build();
    }
}
