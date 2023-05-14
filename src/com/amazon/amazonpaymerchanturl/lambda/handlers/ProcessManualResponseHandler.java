package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.ProcessInvestigationRequest;
import com.amazon.amazonpaymerchanturl.model.URLReviewResponse;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda.lambdaFunctionName;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PROCESS_MANUAL_INVESTIGATION_FAILURE_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PROCESS_MANUAL_INVESTIGATION_SUCCESS_METRICS;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.utils.HandlersUtil.createClientReferenceGroupIdUrl;

/**
 * ProcessManualResponseHandler processes response received from ManualReview of a given URL.
 */
@Builder
@RequiredArgsConstructor
@Log4j2
public class ProcessManualResponseHandler {

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;

    public ProcessManualResponseHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapper();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.urlInvestigationDDBAdapter = lambdaComponent.providesUrlInvestigationDDBAdapter();
    }

    /**
     * handleRequest entry point for ProcessManualResponse Lambda to process response of ManualReview.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        final String lambdaName = lambdaFunctionName(context);
        log.info(lambdaName + "Lambda invoked");

        ProcessInvestigationRequest processInvestigationRequest;
        try {
            processInvestigationRequest = objectMapper.readValue(inputStream, ProcessInvestigationRequest.class);
        } catch (Exception e) {
            log.error("Exception: {} is reported in lambda: {} ", e, lambdaName);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_MANUAL_INVESTIGATION_FAILURE_METRICS);
            lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                    .statusCode(FAILED_STATUS_CODE)
                    .outputStream(outputStream)
                    .lambdaName(lambdaName)
                    .response("input parsing failed.")
                    .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                    .mapper(objectMapper)
                    .build());
            return;
        }

        log.info(lambdaName + " is called with input: {}", processInvestigationRequest);
        int responseStatusCode;
        try {
            if (Objects.nonNull(processInvestigationRequest.getInvestigationId())) {
                responseStatusCode = getAndUpdateUrlInvestigationDDBEntry(processInvestigationRequest);
            } else {
                responseStatusCode = FAILED_STATUS_CODE;
                log.info("Invalid Input: investigationId is null");
                cloudWatchMetricsHelper
                        .publishRecordCountMetricToCloudWatch(PROCESS_MANUAL_INVESTIGATION_FAILURE_METRICS);
            }
        } catch (Exception e) {
            log.error("Exception: {} is reported in lambda: {} for input: {}", e, lambdaName,
                    processInvestigationRequest);
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PROCESS_MANUAL_INVESTIGATION_FAILURE_METRICS);
            responseStatusCode = FAILED_STATUS_CODE;
        }

        final URLReviewResponse response = URLReviewResponse.builder()
                .subInvestigationStatus(processInvestigationRequest.getInvestigationStatus())
                .subInvestigationType(SubInvestigationType.MANUAL.getSubInvestigationType())
                .reviewTime(Instant.now().toEpochMilli())
                .build();

        /*
        Note : MetricFilter syntax pattern should be updated in CDK package accordingly
        if there is a change in Log message
        */
        log.info("[PROCESSED_PARAGON_RESPONSE] Url status after process manual response : {} for caseId: {}, " +
                        "clientRefGrpId: {} and url: {}",
                response.getSubInvestigationStatus(), processInvestigationRequest.getCaseId(),
                processInvestigationRequest.getClientReferenceGroupId(), processInvestigationRequest.getUrl());
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(responseStatusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(response)
                .cloudWatchMetricsHelper(cloudWatchMetricsHelper)
                .mapper(objectMapper)
                .build());
    }

    /**
     * This method fetch the ddb entry and then call to update the ddb entry.
     *
     * @param processInvestigationRequest This is recieved investigation response.
     * @return int responseStatusCode.
     */
    private int getAndUpdateUrlInvestigationDDBEntry(final ProcessInvestigationRequest processInvestigationRequest) {
        int responseStatusCode = SUCCESS_STATUS_CODE;
        UrlInvestigationItem urlInvestigationItem = urlInvestigationDDBAdapter.
                loadEntry(processInvestigationRequest.getInvestigationId(),
                        SubInvestigationType.MANUAL.getSubInvestigationType());
        if (Objects.nonNull(urlInvestigationItem)) {
            log.info("For InvestigationId: {} and SubInvestigationType: Manual, SubInvestigationStatus: {} "
                            + "is Received from UrlInvestigation table", urlInvestigationItem.getInvestigationId(),
                    urlInvestigationItem.getSubInvestigationStatus());

            updateUrlInvestigationDDBEntry(urlInvestigationItem, processInvestigationRequest);
        } else {
            log.info("No DDB entry found corresponding to InvestigationId: {} and SubInvestigationType: Manual, " +
                    "hence creating new DDB Entry in UrlInvestigationTable",
                    processInvestigationRequest.getInvestigationId());
            urlInvestigationDDBAdapter.createOrUpdateEntry(createUrlInvestigationItem(processInvestigationRequest));
        }

        cloudWatchMetricsHelper
                .publishRecordCountMetricToCloudWatch(PROCESS_MANUAL_INVESTIGATION_SUCCESS_METRICS);

        return responseStatusCode;
    }

    /**
     * This method updates the UrlInvestigationDDBEntry.
     *
     * @param urlInvestigationItem This is existing database entry.
     * @param processInvestigationRequest This is the investigation response.
     */
    private void updateUrlInvestigationDDBEntry(final UrlInvestigationItem urlInvestigationItem,
                                                final ProcessInvestigationRequest processInvestigationRequest) {
        if (!StringUtils.equals(urlInvestigationItem.getSubInvestigationStatus(),
                processInvestigationRequest.getInvestigationStatus())
                || !StringUtils.equals(urlInvestigationItem.getReviewInfo(),
                processInvestigationRequest.getReviewInfo())) {
            urlInvestigationItem.setReviewInfo(processInvestigationRequest.getReviewInfo());
            urlInvestigationItem.setSubInvestigationStatus(processInvestigationRequest.getInvestigationStatus());
            urlInvestigationItem.setReviewEndTime(Instant.now().toEpochMilli());
            urlInvestigationDDBAdapter.createOrUpdateEntry(urlInvestigationItem);
        }
    }

    private UrlInvestigationItem createUrlInvestigationItem(
            final ProcessInvestigationRequest processInvestigationRequest) {
        Long currentEpochTime = Instant.now().toEpochMilli();
        return  UrlInvestigationItem.builder()
                .investigationId(processInvestigationRequest.getInvestigationId())
                .subInvestigationType(SubInvestigationType.MANUAL.getSubInvestigationType())
                .investigationType(processInvestigationRequest.getInvestigationType())
                .clientReferenceGroupIdUrl(createClientReferenceGroupIdUrl(
                        processInvestigationRequest.getClientReferenceGroupId(),
                        processInvestigationRequest.getUrl()))
                .subInvestigationId(String.valueOf(processInvestigationRequest.getCaseId()))
                .subInvestigationStatus(processInvestigationRequest.getInvestigationStatus())
                .reviewInfo(processInvestigationRequest.getReviewInfo())
                .reviewStartTime(currentEpochTime)
                .reviewEndTime(currentEpochTime)
                .reviewStartDate(Instant.ofEpochMilli(currentEpochTime).truncatedTo(ChronoUnit.DAYS).toEpochMilli())
                .build();
    }
}
