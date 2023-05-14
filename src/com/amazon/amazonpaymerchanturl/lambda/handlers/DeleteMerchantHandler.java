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
import com.amazon.amazonpaymerchanturl.model.DeleteMerchantResponse;
import com.amazon.amazonpaymerchanturl.processor.DeleteUrlProcessor;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants
        .DELETE_MERCHANT_FAILURE_METRICS_SERVER_ERROR;

/**
 * DeleteMerchantHandler delete the merchant from dvs and everC.
 */
@RequiredArgsConstructor
@Log4j2
public class DeleteMerchantHandler {

    private final LambdaComponent lambdaComponent;
    private final JSONObjectMapperUtil jsonObjectMapperUtil;
    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final DeleteUrlProcessor deleteUrlProcessor;

    public DeleteMerchantHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.jsonObjectMapperUtil = lambdaComponent.providesJSONObjectMapperUtil();
        this.domainValidationDDBAdapter = lambdaComponent.providesDomainValidationDDBAdapter();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.deleteUrlProcessor = lambdaComponent.providesDeleteUrlProcessor();
    }

    /**
     * handleRequest entry point for delete merchant use case.
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
        final String clientReferenceGroupId;
        try {
            clientReferenceGroupId = getClientReferenceGroupId(pathParams);
        } catch (AmazonPayMerchantURLInvalidInputException e) {
            log.error(String.format("Exception occur in lambda: %s while validating the path parameters",
                    lambdaName), e);
            return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_BAD_REQUEST)
                    .withBody(e.getMessage());
        }

        log.info(String.format("%s is called for clientReferenceGroupId: %s", lambdaName, clientReferenceGroupId));

        List<String> successUrls = new ArrayList<>();
        List<String> failedUrls = new ArrayList<>();
        try {
            final List<AmazonPayDomainValidationItem> amazonPayDomainValidationItemList
                    = domainValidationDDBAdapter.queryByClientReferenceGroupId(clientReferenceGroupId);
            if (CollectionUtils.isEmpty(amazonPayDomainValidationItemList)) {
                return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_NOT_FOUND)
                        .withBody("Resource Not Present for clientReferenceGroupId: " + clientReferenceGroupId);
            }
            amazonPayDomainValidationItemList.forEach(amazonPayDomainValidationItem -> {
                log.info("Processing url: {} for clientReferenceGroupId: {}",
                        amazonPayDomainValidationItem.getUrl(), clientReferenceGroupId);
                boolean status = processDeleteUrlRequest(amazonPayDomainValidationItem)
                        ? successUrls.add(amazonPayDomainValidationItem.getUrl())
                        : failedUrls.add(amazonPayDomainValidationItem.getUrl());
            });

            final DeleteMerchantResponse deleteMerchantResponse = DeleteMerchantResponse.builder()
                    .clientReferenceGroupId(clientReferenceGroupId)
                    .successUrls(successUrls)
                    .failedUrls(failedUrls)
                    .build();
            final String responseBody = jsonObjectMapperUtil.serialize(deleteMerchantResponse);

            return apiGatewayProxyResponseEvent.withStatusCode(
                    CollectionUtils.isEmpty(failedUrls) ? HttpStatus.SC_OK : HttpStatus.SC_MULTI_STATUS)
                    .withBody(responseBody);
        } catch (AmazonPayDomainValidationDAONonRetryableException | AmazonPayMerchantURLNonRetryableException e) {
            log.error("Non-retryable exception received. while deleting the clientReferenceGroupId: {}",
                    clientReferenceGroupId, e);
            return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_BAD_REQUEST).withBody(e.getMessage());
        } catch (AmazonPayDomainValidationDAORetryableException e) {
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(DELETE_MERCHANT_FAILURE_METRICS_SERVER_ERROR);
            log.error("Retryable exception received. while deleting the clientReferenceGroupId: {}",
                    clientReferenceGroupId, e);
            return apiGatewayProxyResponseEvent.withStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .withBody(e.getMessage());
        }
    }

    private boolean processDeleteUrlRequest(final AmazonPayDomainValidationItem amazonPayDomainValidationItem) {
        try {
            final boolean isVendorDeboardRequired = !StringUtils.equals(SubInvestigationType.UPFRONT_VALIDATION
                    .getSubInvestigationType(), amazonPayDomainValidationItem.getSubInvestigationType());
            deleteUrlProcessor.process(amazonPayDomainValidationItem.getClientReferenceGroupId(),
                    amazonPayDomainValidationItem.getUrl(), isVendorDeboardRequired,
                    SubInvestigationType.AUTO_HEAVYWEIGHT.getSubInvestigationType());
            return true;
        } catch (AmazonPayMerchantURLNonRetryableException e) {
            log.error("Non-retryable exception received. while deleting the url: {} for "
                            + "clientReferenceGroupId: {}", amazonPayDomainValidationItem.getUrl(),
                    amazonPayDomainValidationItem.getClientReferenceGroupId(), e);
        } catch (AmazonPayMerchantURLRetryableException e) {
            log.error("Retryable exception received. while deleting the url: {} for "
                            + "clientReferenceGroupId: {}", amazonPayDomainValidationItem.getUrl(),
                    amazonPayDomainValidationItem.getClientReferenceGroupId(), e);
        }
        return false;
    }

    private String getClientReferenceGroupId(final Map<String, String> pathParams) {
        if (Objects.isNull(pathParams)
                || StringUtils.isEmpty(pathParams.get(PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY))) {
            throw new AmazonPayMerchantURLInvalidInputException("Invalid Input exception");
        }
        return pathParams.get(PATH_PARAM_CLIENT_REFERENCE_GROUP_ID_KEY);
    }
}
