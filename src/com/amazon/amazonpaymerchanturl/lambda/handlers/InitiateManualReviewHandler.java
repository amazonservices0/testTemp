package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.ParagonInvestigationServiceAdapter;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.InitiateManualReviewRequest;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.WebsiteReviewRequest;
import com.amazon.amazonpaymerchanturl.model.WebsiteReviewResponse;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazon.paragoninvestigationservice.Reason;
import com.amazon.urlvendorreviewmodel.type.SubInvestigationStatus;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationType.PERIODIC_INVESTIGATION;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.MANUAL_URL_REVIEW_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.MANUAL_URL_REVIEW_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getInvestigationRegionIdentifier;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getMarketplaceId;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getMerchantId;
import static com.amazon.amazonpaymerchanturl.utils.HandlersUtil.createClientReferenceGroupIdUrl;
import static com.amazon.amazonpaymerchanturl.utils.URLCaseSensitivityConvertorUtil.getLowerCaseConvertedURL;

/**
 * InitiateManualReviewHandler initiates ManualReview for a given URL.
 */
@RequiredArgsConstructor
@Log4j2
public class InitiateManualReviewHandler {

    private static final String AMAZON_PAY_BUSINESS = "AmazonPay";
    private static final String CONTEXT_TYPE = "requesterInfo";
    private static final Long TENANT_ID = 178L;
    private static final String HYPHEN = "-";

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper mapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final Map<String, String> queueIdMap;
    private final Map<String, Map<String, String>> queueIdToRiskLevelMap;
    private final Map<String, Map<String, String>> queueIdForPeriodicReviewToRiskLevelMap;
    private final ParagonInvestigationServiceAdapter paragonInvestigationServiceAdapter;

    public InitiateManualReviewHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.mapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.urlInvestigationDDBAdapter = lambdaComponent.providesUrlInvestigationDDBAdapter();
        this.queueIdMap = lambdaComponent.providesQueueIDMap();
        this.queueIdToRiskLevelMap = lambdaComponent.providesQueueIDToRiskLevelMap();
        this.queueIdForPeriodicReviewToRiskLevelMap = lambdaComponent.providesQueueIDForPeriodicReviewToRiskLevelMap();
        this.paragonInvestigationServiceAdapter = lambdaComponent.getParagonInvestigationServiceAdapter();
    }

    /**
     * handleRequest entry point for InitiateManualReview Lambda to initiate ManualReview.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context.
     */
    //ToDo: SIM https://sim.amazon.com/issues/D27589603
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        log.info("InitiateManualReview Lambda invoked");

        String functionName = lambdaFunctionName(context);
        String inputJson;
        InitiateManualReviewRequest initiateManualReviewRequest;
        try {
            initiateManualReviewRequest = mapper.readValue(inputStream, InitiateManualReviewRequest.class);
            inputJson = mapper.writeValueAsString(initiateManualReviewRequest);
        } catch (Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(MANUAL_URL_REVIEW_FAILURE_METRICS);
            sendResponse(FAILED_STATUS_CODE, outputStream, functionName, "input parsing failed");
            return;
        }

        log.info(functionName + " is called with input: {}", inputJson);

        final String reviewURL = initiateManualReviewRequest.getReviewURL();
        if (StringUtils.isEmpty(reviewURL)) {
            sendResponse(SUCCESS_STATUS_CODE, outputStream, functionName,
                    "info. required for review is empty(null)");
            return;
        }

        int responseStatusCode = SUCCESS_STATUS_CODE;

        try {
            String url = getLowerCaseConvertedURL(reviewURL);
            log.info("Lowercase convert Url for manual Investigation {}", url);
            final boolean responseStatus = initiateURLInvestigation(initiateManualReviewRequest, url);
            if (!responseStatus) {
                responseStatusCode = FAILED_STATUS_CODE;
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(MANUAL_URL_REVIEW_FAILURE_METRICS);
            } else {
                cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(MANUAL_URL_REVIEW_SUCCESS_METRICS);
            }
        } catch (Exception e) {
            responseStatusCode = FAILED_STATUS_CODE;
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(MANUAL_URL_REVIEW_FAILURE_METRICS);
        }
        sendResponse(responseStatusCode, outputStream, functionName, null);
    }

    private void sendResponse(int statusCode, OutputStream outputStream, String functionName,
                              String message) {
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(statusCode)
                .outputStream(outputStream)
                .lambdaName(functionName)
                .details(message)
                .cloudWatchMetricsHelper(this.cloudWatchMetricsHelper)
                .mapper(this.mapper)
                .build());

    }

    private boolean initiateURLInvestigation(InitiateManualReviewRequest input, String url) {

        UrlInvestigationItem urlInvestigationItem;
        boolean entryCreated;
        WebsiteReviewResponse paragonInvestigationResponse;
        try {
            paragonInvestigationResponse = queueInvestigationInParagon(
                    url, input.getClientCustomInformation(), input.getClientReferenceGroupId(), input.getRiskLevel(),
                    input.getInvestigationType());
            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[PARAGON_CASE_CREATED] Created Paragon case successfully with caseId: {} and RiskLevel {}" +
                            " for ClientRefGrpId: {}, url: {} and investigationType: {}",
                    paragonInvestigationResponse.getInvestigationId(), input.getRiskLevel(),
                    input.getClientReferenceGroupId(), url, input.getInvestigationType());
        } catch (Exception e) {
            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[PARAGON_CREATION_FAILURE] Error occur while queueing investigation in paragon for " +
                    "clientRefGrpId: {} and url: {}", input.getClientReferenceGroupId(), url, e);
            return false;
        }

        try {
            log.info("Origin url: {}. Paragon queue investigation response: {}", url, paragonInvestigationResponse);
            Long caseCreationTime = Instant.now().toEpochMilli();
            Long caseCreationDate = Instant.ofEpochMilli(caseCreationTime).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
            urlInvestigationItem = createUrlInvestigationItem(input, caseCreationTime, caseCreationDate,
                    paragonInvestigationResponse.getInvestigationId(), url);

            entryCreated = urlInvestigationDDBAdapter.createEntryIfNotExists(urlInvestigationItem);
        } catch (Exception e) {
            log.info("Error creating entry in DB", e);
            return false;
        }

        if (!entryCreated) {
            /*
            Note : MetricFilter syntax pattern should be updated in CDK package accordingly
            if there is a change in Log message
            */
            log.info("[DUPLICATE_CASE] ClientReferenceGroupId {}, URL {} already present",
                    input.getClientReferenceGroupId(), url);
        }
        return true;
    }

    private WebsiteReviewResponse queueInvestigationInParagon(@NonNull final String originURL,
                                                              @NonNull final String clientCustomInformation,
                                                              @NonNull final String clientReferenceGroupId,
                                                              final String riskLevel,
                                                              final String investigationType) {

        String investigationQueue = fetchInvestigationQueue(clientCustomInformation, riskLevel, investigationType);

        log.info("Selected investigation Queue: {} for clientCustomInformation: {} and clientReferenceGroupId: {}",
                investigationQueue, clientCustomInformation, clientReferenceGroupId);

        final List<Reason> reason = new ArrayList<>();
        final Map<String, List<String>> context = new HashMap<>();
        context.put(CONTEXT_TYPE, Collections.singletonList(originURL));
        final WebsiteReviewRequest paragonInvestigationRequest = WebsiteReviewRequest
                .builder()
                .merchantId(getMerchantId(clientReferenceGroupId))
                .investigationQueueName(investigationQueue)
                .marketplaceId(getMarketplaceId(clientReferenceGroupId))
                .tenantId(TENANT_ID)
                .reasons(reason)
                .context(context)
                .build();

        return paragonInvestigationServiceAdapter
                .createParagonInvestigation(paragonInvestigationRequest);
    }

    private String fetchInvestigationQueue(String clientCustomInformation, String riskLevel, String investigationType) {
        final String investigationRegionIdentifier = getInvestigationRegionIdentifier(clientCustomInformation);
        if (StringUtils.isNotBlank(investigationType) && StringUtils.isNotBlank(riskLevel)
                && StringUtils.equalsIgnoreCase(investigationType, PERIODIC_INVESTIGATION.getInvestigationType())
                && !queueIdForPeriodicReviewToRiskLevelMap.get(investigationRegionIdentifier).isEmpty()) {
            return queueIdForPeriodicReviewToRiskLevelMap.get(investigationRegionIdentifier)
                    .get(riskLevel.toUpperCase(Locale.ROOT));
        } else if (StringUtils.isNotBlank(riskLevel)
                && !queueIdToRiskLevelMap.get(investigationRegionIdentifier).isEmpty()) {
            return queueIdToRiskLevelMap.get(investigationRegionIdentifier)
                    .get(riskLevel.toUpperCase(Locale.ROOT));
        } else {
            return queueIdMap.get(investigationRegionIdentifier);
        }
    }

    private UrlInvestigationItem createUrlInvestigationItem(final InitiateManualReviewRequest input,
                                                            @NonNull final Long caseCreationTime,
                                                            @NonNull final Long caseCreationDate,
                                                            @NonNull final Long caseId, @NonNull String url) {
        return UrlInvestigationItem.builder()
                .clientReferenceGroupIdUrl(createClientReferenceGroupIdUrl(input.getClientReferenceGroupId(), url))
                .investigationId(input.getInvestigationId())
                .investigationType(input.getInvestigationType())
                .subInvestigationId(caseId.toString())
                .subInvestigationType(SubInvestigationType.MANUAL.getSubInvestigationType())
                .subInvestigationStatus(SubInvestigationStatus.IN_REVIEW.getSubInvestigationStatus())
                .subInvestigationTaskToken(input.getTaskToken())
                .reviewStartTime(caseCreationTime)
                .reviewStartDate(caseCreationDate)
                .build();
    }
}
