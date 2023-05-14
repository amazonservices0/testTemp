package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.helper.WeblabHelper;
import com.amazon.amazonpaymerchanturl.model.Inspection;
import com.amazon.amazonpaymerchanturl.model.GetLatestVendorReviewResponse;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.amazonpaymerchanturl.utils.URLStandardizeUtil;
import com.amazon.urlvendorreviewlib.component.DaggerUrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.component.UrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewClientException;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewServerException;
import com.amazon.urlvendorreviewlib.factory.VendorReviewHandlerFactory;
import com.amazon.urlvendorreviewmodel.model.EvidenceSpec;
import com.amazon.urlvendorreviewmodel.model.InspectionSpec;
import com.amazon.urlvendorreviewmodel.model.ReviewInfo;
import com.amazon.urlvendorreviewmodel.model.RiskSpec;
import com.amazon.urlvendorreviewmodel.request.GetDetailedInspectionRequest;
import com.amazon.urlvendorreviewmodel.type.SubInvestigationType;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.common.collect.ImmutableSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS_BAD_REQUEST;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS_DDB_ENTRY_NOT_FOUND;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.GET_LATEST_VENDOR_REVIEW_RESPONSE_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.BAD_REQUEST_FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.INTERNAL_SERVER_ERROR;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.BAD_REQUEST;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.NOT_FOUND;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.NOT_FOUND_FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EVERCOMPLIANT_ID;
import static com.amazon.amazonpaymerchanturl.utils.HandlersUtil.createClientReferenceGroupIdUrl;

/**
 * GetLatestVendorReviewResponseHandler returns the latest vendor review response
 */
@RequiredArgsConstructor
@Log4j2
public class GetLatestVendorReviewResponseHandler {

    private static final String PATH_PARAM_CLIENT_REF_GROUP_ID_KEY = "clientReferenceGroupId";
    private static final String QUERY_PARAM_URL_KEY = "url";
    private static final String EVIDENCE_TYPE_SCREENSHOT = "screenshot";
    private static final Set<String> VENDOR_REVIEW_TYPES = ImmutableSet.of(
            SubInvestigationType.AUTO_HEAVYWEIGHT.getSubInvestigationType(),
            SubInvestigationType.AUTO_LIGHTWEIGHT.getSubInvestigationType(),
            SubInvestigationType.AUTO_MONITORING.getSubInvestigationType());

    private final LambdaComponent lambdaComponent;
    private final JSONObjectMapperUtil jsonObjectMapperUtil;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final UrlVendorReviewLibComponent urlVendorReviewLibComponent;
    private final VendorReviewHandlerFactory vendorReviewHandlerFactory;
    private final WeblabHelper weblabHelper;

    public GetLatestVendorReviewResponseHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.jsonObjectMapperUtil = lambdaComponent.providesJSONObjectMapperUtil();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.urlInvestigationDDBAdapter = lambdaComponent.providesUrlInvestigationDDBAdapter();
        urlVendorReviewLibComponent = DaggerUrlVendorReviewLibComponent.create();
        vendorReviewHandlerFactory = urlVendorReviewLibComponent.getVendorReviewHandlerFactory();
        weblabHelper = lambdaComponent.provideWeblabHelper();
    }

    /**
     * handleRequest entry point for GetLatestVendorReviewResponse Lambda
     * @param apiGatewayProxyRequestEvent apiGatewayProxyRequestEvent
     * @param context context
     */
    public APIGatewayProxyResponseEvent handleRequest(
            final APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent,
            final Context context) {

        String lambdaFunctionName = lambdaFunctionName(context);
        log.info(lambdaFunctionName + " lambda invoked.");

        Map<String, String> pathParameters = apiGatewayProxyRequestEvent.getPathParameters();
        Map<String, String> queryStringParameters = apiGatewayProxyRequestEvent.getQueryStringParameters();

        if (!isValidRequest(pathParameters, queryStringParameters)) {
            log.error("Invalid Request Params while calling GetLatestVendorReviewResponse lambda");
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS_BAD_REQUEST);
            return constructAPIGatewayProxyResponseEvent(BAD_REQUEST_FAILED_STATUS_CODE, BAD_REQUEST);
        }

        final String clientReferenceGroupId = pathParameters.get(PATH_PARAM_CLIENT_REF_GROUP_ID_KEY);
        final String url = queryStringParameters.get(QUERY_PARAM_URL_KEY);
        final String clientRefGroupIdDomain = createClientReferenceGroupIdUrl(clientReferenceGroupId, url);
        UrlInvestigationItem latestUrlInvestigationItem;
        try {
            log.info("Getting latest vendor review response for clientReferenceGroupId: {} and url: {}",
                    clientReferenceGroupId, url);

            // Getting DDB entries from UrlInvestigation table based on clientRefGroupIdDomain.
            final List<UrlInvestigationItem> urlInvestigationItemList = getUrlInvestigationItemsFromDDB(
                    clientRefGroupIdDomain, clientReferenceGroupId, url);

            latestUrlInvestigationItem = getLatestUrlInvestigationItemBasedOnReviewEndTime(
                    urlInvestigationItemList).orElse(null);

        } catch (AmazonPayDomainValidationDAONonRetryableException e) {
            log.error("AmazonPayDomainValidationDAONonRetryableException while getting record DDB Table", e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS);
            return constructAPIGatewayProxyResponseEvent(
                    BAD_REQUEST_FAILED_STATUS_CODE, BAD_REQUEST);
        } catch (AmazonPayDomainValidationDAORetryableException e) {
            log.error("AmazonPayDomainValidationDAORetryableException while getting record DDB Table", e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS);
            return constructAPIGatewayProxyResponseEvent(FAILED_STATUS_CODE, INTERNAL_SERVER_ERROR);
        }

        if (latestUrlInvestigationItem == null) {
            log.info("No DDB Entry found for given clientRefGroupIdDomain: {} in UrlInvestigation DDB table",
                    clientRefGroupIdDomain);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS_DDB_ENTRY_NOT_FOUND);
            return constructAPIGatewayProxyResponseEvent(NOT_FOUND_FAILED_STATUS_CODE, NOT_FOUND);
        }

        String reviewInfoJsonString = latestUrlInvestigationItem.getReviewInfo();

        if (StringUtils.isEmpty(reviewInfoJsonString)) {
            log.info("No reviewInfo found corresponding to given clientReferenceId: {} and url: {}",
                    clientReferenceGroupId, url);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS);
            return constructAPIGatewayProxyResponseEvent(NOT_FOUND_FAILED_STATUS_CODE, NOT_FOUND);
        }

        return deserializeReviewInfoAndBuildGetLatestVendorReviewResponse(clientReferenceGroupId, url,
                SubInvestigationType.fromValue(latestUrlInvestigationItem.getSubInvestigationType()),
                reviewInfoJsonString);
    }

    /*
     * Get the UrlInvestigation Items from DDB based on ClientReferenceGroupId-Url
     * If No Entry found in DB, Get the UrlInvestigation item for ClientReferenceGroupId-StandardizedUrl
     */
    private List<UrlInvestigationItem> getUrlInvestigationItemsFromDDB(final String clientRefGroupIdDomain,
                                                                       final String clientReferenceGroupId,
                                                                       final String url) {
        List<UrlInvestigationItem> urlInvestigationItemList = urlInvestigationDDBAdapter.
                queryOnClientRefGroupIdDomainAsSecondaryIndex(clientRefGroupIdDomain);

        //TODO : Remove the below block after De-duping the variant URLs in DDB
        if (weblabHelper.isWeblabDialedUpForDedupingVariantUrls(clientReferenceGroupId) &&
                CollectionUtils.isEmpty(urlInvestigationItemList)) {
            final String standardizedUrl = URLStandardizeUtil.standardize(url);
            final String clientRefGroupIdStandardizedUrl = createClientReferenceGroupIdUrl(clientReferenceGroupId,
                    standardizedUrl);
            log.info(String.format("No DDB Entry found for clientRefGroupIdDomain: %s. Looking for StandardizedUrl " +
                "entry :%s in UrlInvestigation DDB Table", clientReferenceGroupId, clientRefGroupIdStandardizedUrl));
            urlInvestigationItemList = urlInvestigationDDBAdapter.
                    queryOnClientRefGroupIdDomainAsSecondaryIndex(clientRefGroupIdStandardizedUrl);
        }
        return urlInvestigationItemList;
    }

    private APIGatewayProxyResponseEvent constructAPIGatewayProxyResponseEvent(Integer statusCode, String body) {
        return new APIGatewayProxyResponseEvent().withStatusCode(statusCode).withBody(body);
    }

    private boolean isValidRequest(Map<String, String> pathParameters, Map<String, String> queryStringParameters) {
        return (pathParameters != null && pathParameters.containsKey(PATH_PARAM_CLIENT_REF_GROUP_ID_KEY)
                && queryStringParameters != null
                && queryStringParameters.containsKey(QUERY_PARAM_URL_KEY));
    }

    private Optional<UrlInvestigationItem> getLatestUrlInvestigationItemBasedOnReviewEndTime(
            final List<UrlInvestigationItem> urlInvestigationItemList) {

        if (CollectionUtils.isEmpty(urlInvestigationItemList)) {
            return Optional.empty();
        }

        // Returns getLatestUrlInvestigationItemBasedOnReviewEndTime
        // Filtering is done on the basis of vendorReviewTypes and ReviewEndTime Field not null
        return urlInvestigationItemList.stream()
                .filter(urlInvestigationItem -> VENDOR_REVIEW_TYPES.contains(
                        urlInvestigationItem.getSubInvestigationType())
                        &&  urlInvestigationItem.getReviewEndTime() != null)
                .max(Comparator.comparing(UrlInvestigationItem::getReviewEndTime));
    }

    private APIGatewayProxyResponseEvent deserializeReviewInfoAndBuildGetLatestVendorReviewResponse(
            String clientReferenceGroupId,
            String url,
            SubInvestigationType subInvestigationType,
            String reviewInfoJsonString) {
        try {
            final ReviewInfo reviewInfo = jsonObjectMapperUtil.deserialize(reviewInfoJsonString, ReviewInfo.class);

            if (reviewInfo == null || reviewInfo.getInspections() == null || reviewInfo.getInspections().isEmpty()) {
                log.info("Data Not Found: Either getting empty reviewInfo object or empty inspections list");
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                        GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS);
                return constructAPIGatewayProxyResponseEvent(NOT_FOUND_FAILED_STATUS_CODE, NOT_FOUND);
            }

            final List<InspectionSpec> detailedInspections = getInspectionsWithDetailedInspectionSpec(
                    reviewInfo, subInvestigationType);

            //Map reviewInfo data to Vendor ReviewResponse
            final GetLatestVendorReviewResponse getLatestVendorReviewResponse = buildGetLatestVendorReviewResponse(
                    clientReferenceGroupId, url, subInvestigationType, detailedInspections, reviewInfo.getRiskSpec());
            log.info("Successfully updated the getLatestVendorReviewResponse: {} with detailed inspectionSpec",
                    getLatestVendorReviewResponse);

            final String latestVendorReviewResponse = jsonObjectMapperUtil.
                    serialize(getLatestVendorReviewResponse);

            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_SUCCESS_METRICS);
            return constructAPIGatewayProxyResponseEvent(SUCCESS_STATUS_CODE, latestVendorReviewResponse);
        } catch (AmazonPayMerchantURLNonRetryableException e) {
            log.error("AmazonPayMerchantURLNonRetryableException while deserialising/serialising " +
                    "reviewInfo or latestVendorReviewResponse", e);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                    GET_LATEST_VENDOR_REVIEW_RESPONSE_FAILURE_METRICS);
            return constructAPIGatewayProxyResponseEvent(BAD_REQUEST_FAILED_STATUS_CODE, BAD_REQUEST);
        }
    }

    private GetLatestVendorReviewResponse buildGetLatestVendorReviewResponse(
            final String clientReferenceGroupId,
            final String url,
            final SubInvestigationType subInvestigationType,
            final List<InspectionSpec> inspectionSpecList,
            final RiskSpec riskSpec) {
        List<Inspection> inspections = inspectionSpecList.stream()
                .map(inspectionSpec -> Inspection.builder()
                        .inspectionType(inspectionSpec.getInspectionType())
                        .inspectionTypeName(inspectionSpec.getInspectionName())
                        .inspectionCategory(inspectionSpec.getInspectionCategory())
                        .inspectionCategoryName(inspectionSpec.getInspectionCategoryName())
                        .riskLevel((Objects.isNull(inspectionSpec.getRiskSpec())) ? null
                                : inspectionSpec.getRiskSpec().getRiskLevel())
                        .evidences(inspectionSpec.getEvidences())
                        .build())
                .collect(Collectors.toList());

        return GetLatestVendorReviewResponse.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .reviewType(subInvestigationType)
                .riskLevel((Objects.isNull(riskSpec)) ? null : riskSpec.getRiskLevel())
                .inspections(inspections)
                .build();
    }

    private GetDetailedInspectionRequest buildGetDetailedInspectionRequest(
            final SubInvestigationType subInvestigationType,
            final String externalClientId,
            final String urlId,
            final String inspectionId) {
        return GetDetailedInspectionRequest.builder()
                .subInvestigationType(subInvestigationType.getSubInvestigationType())
                .externalClientId(externalClientId)
                .urlId(urlId)
                .inspectionId(inspectionId)
                .build();
    }

    private List<InspectionSpec> getInspectionsWithDetailedInspectionSpec(
            final ReviewInfo reviewInfo,
            final SubInvestigationType subInvestigationType) {
        // Calling vendor and updating the evidencesList only for EvidenceType Screenshot
        return reviewInfo.getInspections().stream()
                .peek(inspectionSpec -> {
                    if (Objects.nonNull(inspectionSpec.getEvidences())
                            && inspectionSpec.getEvidences().stream().anyMatch(evidenceSpec ->
                            evidenceSpec.getEvidenceType().equals(EVIDENCE_TYPE_SCREENSHOT))) {
                        try {
                            inspectionSpec.setEvidences(vendorCallToGetEvidenceSpecList(
                                    subInvestigationType, reviewInfo.getExternalClientId(),
                                    reviewInfo.getUrlId(), inspectionSpec.getInspectionId()));
                        } catch (UrlVendorReviewClientException | UrlVendorReviewServerException e) {
                            log.error("Exception while receiving response from Sparta " +
                                    "while fetching detailed inspection.", e);
                        }
                    }
                })
                .collect(Collectors.toList());
    }

    private List<EvidenceSpec> vendorCallToGetEvidenceSpecList(final SubInvestigationType subInvestigationType,
                                                               final String externalClientId,
                                                               final String urlId,
                                                               final String inspectionId) {
        final GetDetailedInspectionRequest getDetailedInspectionRequest = buildGetDetailedInspectionRequest(
                subInvestigationType, externalClientId, urlId, inspectionId);
        log.info("Calling Vendor with externalClientId: {}, urlId: {} " +
                        "for evidenceType screenshot to get detailed inspection.", externalClientId, urlId);
            // TODO: Remove hardCode EverCompliant Id, when we have multiple vendors.
            return vendorReviewHandlerFactory
                    .produceVendorHandler(EVERCOMPLIANT_ID)
                    .getDetailedInspection(getDetailedInspectionRequest)
                    .getEvidences();
    }
}
