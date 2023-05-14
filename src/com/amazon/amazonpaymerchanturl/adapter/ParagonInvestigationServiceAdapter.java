package com.amazon.amazonpaymerchanturl.adapter;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLBaseException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLDownstreamFailureException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLInvalidInputException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.WebsiteReviewRequest;
import com.amazon.amazonpaymerchanturl.model.WebsiteReviewResponse;
import com.amazon.paragoninvestigationservice.CreateInvestigationRequest;
import com.amazon.paragoninvestigationservice.CreateInvestigationResponse;
import com.amazon.paragoninvestigationservice.InternalFailureException;
import com.amazon.paragoninvestigationservice.InvalidCRMTypeException;
import com.amazon.paragoninvestigationservice.InvalidRequestException;
import com.amazon.paragoninvestigationservice.NonRetryableDependencyException;
import com.amazon.paragoninvestigationservice.NotAuthorizedException;
import com.amazon.paragoninvestigationservice.ParagonInvestigationServiceClient;
import com.amazon.paragoninvestigationservice.RetryableDependencyException;
import com.amazon.paragoninvestigationservice.SubjectValidationException;
import lombok.extern.log4j.Log4j2;
import lombok.NonNull;

import javax.inject.Inject;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PARAGON_INVESTIGATION_ERROR_METRIC;

/**
 * This Adapter holds the logic to call Paragon Investigation Service.
 */
@Log4j2
public class ParagonInvestigationServiceAdapter {

    private final ParagonInvestigationServiceClient paragonInvestigationServiceClient;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;

    @Inject
    public  ParagonInvestigationServiceAdapter(
            @NonNull final ParagonInvestigationServiceClient paragonInvestigationServiceClient,
            @NonNull final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        this.paragonInvestigationServiceClient = paragonInvestigationServiceClient;
        this.cloudWatchMetricsHelper = cloudWatchMetricsHelper;
    }

    /**
     * Calls Paragon Investigation Service to create a case.
     *
     * @param request InvestigationRequest
     * @return InvestigationResponse containing investigationId and investigationStatus
     */
    public WebsiteReviewResponse createParagonInvestigation(@NonNull final WebsiteReviewRequest request) {

        log.info("Creating investigation for merchant: {} in marketplace: {}",
                request.getMerchantId(), request.getMarketplaceId());
        try {
            CreateInvestigationRequest callRequest = CreateInvestigationRequest.builder()
                    .withEncryptedMarketplaceId(request.getMarketplaceId())
                    .withSubjectValue(request.getMerchantId())
                    .withQueue(request.getInvestigationQueueName())
                    .withReasons(request.getReasons())
                    .withTenantId(request.getTenantId())
                    .withContext(request.getContext())
                    .build();

            CreateInvestigationResponse createInvestigationResponse = paragonInvestigationServiceClient
                    .newCreateInvestigationCall().call(callRequest);

            log.info("Created investigation in Paragon with caseId: {} for merchant: {} in marketplace: {}",
                    createInvestigationResponse.getId(), request.getMerchantId(), request.getMarketplaceId());
            return WebsiteReviewResponse.builder()
                    .investigationId(createInvestigationResponse.getId())
                    .investigationStatus(createInvestigationResponse.getStatus())
                    .build();

        } catch (Exception e) {
            cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(PARAGON_INVESTIGATION_ERROR_METRIC);
            throw handleException(e, "Failed to create investigation for merchant: "
                    + request.getMerchantId() + "in marketplace: " + request.getMarketplaceId());
        }
    }


    private AmazonPayMerchantURLBaseException handleException(Exception e, String msg) {

        log.error("Exception Found while creating investigation: Message {} Error {}", msg, e);
        if (e instanceof SubjectValidationException || e instanceof InvalidRequestException) {
            return new AmazonPayMerchantURLInvalidInputException("ParagonInvestigationService Client Exception. "
                    + "The request to Paragon is invalid. " + msg, e);
        } else if (e instanceof RetryableDependencyException) {
            return new AmazonPayMerchantURLRetryableException(
                    "ParagonInvestigationService Service Exception. "
                            + "A Retryable exception was thrown by Paragon. " + msg, e);
        } else if (e instanceof NonRetryableDependencyException || e instanceof InternalFailureException) {
            return new AmazonPayMerchantURLNonRetryableException(
                    "ParagonInvestigationService Service Exception. "
                            + "Non transient exception thrown by Paragon. " + msg, e);
        } else if (e instanceof InvalidCRMTypeException) {
            return new AmazonPayMerchantURLInvalidInputException(
                    "ParagonInvestigationService Client Exception. "
                            + "Case not classified as investigation. " + msg, e);
        } else if (e instanceof NotAuthorizedException) {
            return new AmazonPayMerchantURLDownstreamFailureException(
                    "ParagonInvestigationService Client Exception. "
                            + "User or system not authorized for request. " + msg, e);
        } else {
            return new AmazonPayMerchantURLRetryableException(msg, e);
        }
    }

}
