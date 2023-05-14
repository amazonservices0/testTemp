package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLInvalidInputException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.processor.DeleteUrlProcessor;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import java.util.Map;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY;
import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.QUERY_STRING_PARAM_URL_KEY;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.DELETE_URL_FAILURE_METRICS_DDB_ENTRY_NOT_FOUND;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants
        .DELETE_URL_FAILURE_METRICS_INTERNAL_SERVER_ERROR;

/**
 * DeleteUrlHandler delete the url from dvs and everC.
 */
@RequiredArgsConstructor
@Log4j2
public class DeleteUrlHandler {

    private final LambdaComponent lambdaComponent;
    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final DeleteUrlProcessor deleteUrlProcessor;

    public DeleteUrlHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.domainValidationDDBAdapter = lambdaComponent.providesDomainValidationDDBAdapter();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.deleteUrlProcessor = lambdaComponent.providesDeleteUrlProcessor();
    }

    /**
     * handleRequest entry point for delete url use case.
     *
     * @param apiGatewayProxyRequestEvent  input for the lambda.
     * @param context      context
     * @return api response.
     */
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent,
                                                      final Context context) {
        final String lambdaName = Lambda.lambdaFunctionName(context);

        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = new APIGatewayProxyResponseEvent();
        final Map<String, String> pathParams = apiGatewayProxyRequestEvent.getPathParameters();
        final Map<String, String> queryStringParams = apiGatewayProxyRequestEvent.getQueryStringParameters();
        try {
            validateDeleteUrlPathParamsAndQueryStringParams(pathParams, queryStringParams);
        } catch (AmazonPayMerchantURLInvalidInputException e) {
            log.error(String.format("Exception occur in lambda: %s while validating the path parameters",
                    lambdaName), e);
            return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_BAD_REQUEST)
                    .withBody(e.getMessage());
        }
        final String clientReferenceGroupId = pathParams.get(PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY);
        final String url = queryStringParams.get(QUERY_STRING_PARAM_URL_KEY);
        log.info("{} is called for clientReferenceGroupId: {} and url: {}", lambdaName, clientReferenceGroupId, url);

        try {
            return processDeleteUrlRequest(clientReferenceGroupId, url)
                    ? apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_NO_CONTENT)
                    : apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_NOT_FOUND);
        } catch (AmazonPayDomainValidationDAONonRetryableException | AmazonPayMerchantURLNonRetryableException e) {
            log.error("Non-retryable exception received. while deleting the url: {} for "
                    + "clientReferenceGroupId: {}", url, clientReferenceGroupId, e);
            return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_BAD_REQUEST).withBody(e.getMessage());
        } catch (AmazonPayDomainValidationDAORetryableException | AmazonPayMerchantURLRetryableException e) {
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(DELETE_URL_FAILURE_METRICS_INTERNAL_SERVER_ERROR);
            log.error("Retryable exception received. while deleting the url: {} for "
                    + "clientReferenceGroupId: {}", url, clientReferenceGroupId, e);
            return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .withBody(e.getMessage());
        }
    }

    private boolean processDeleteUrlRequest(final String clientReferenceGroupId, final String url) {
        final AmazonPayDomainValidationItem amazonPayDomainValidationItem
                = domainValidationDDBAdapter.loadEntry(clientReferenceGroupId, url);
        if (Objects.nonNull(amazonPayDomainValidationItem)) {
            final boolean isVendorDeboardRequired = !StringUtils.equals(SubInvestigationType.UPFRONT_VALIDATION
                    .getSubInvestigationType(), amazonPayDomainValidationItem.getSubInvestigationType());
            deleteUrlProcessor.process(clientReferenceGroupId, url, isVendorDeboardRequired,
                    SubInvestigationType.AUTO_HEAVYWEIGHT.getSubInvestigationType());
            return true;
        } else {
            log.info("DDB entry not found for clientReferenceGroupId: {} and url: {}",
                    clientReferenceGroupId, url);
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(DELETE_URL_FAILURE_METRICS_DDB_ENTRY_NOT_FOUND);
            return false;
        }
    }

    private void validateDeleteUrlPathParamsAndQueryStringParams(final Map<String, String> pathParams,
                                                                 final Map<String, String> queryStringParams) {
        if (Objects.isNull(pathParams) || Objects.isNull(queryStringParams)
                || StringUtils.isEmpty(pathParams.get(PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY))
                || StringUtils.isEmpty(queryStringParams.get(QUERY_STRING_PARAM_URL_KEY))) {
            throw new AmazonPayMerchantURLInvalidInputException("Invalid Input exception");
        }
    }
}
