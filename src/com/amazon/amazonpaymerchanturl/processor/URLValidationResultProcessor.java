package com.amazon.amazonpaymerchanturl.processor;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.getUrlValidationStatusMetricMap;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil.getLWAClientId;
import static com.amazon.amazonpaymerchanturl.utils.HandlersUtil.createClientReferenceGroupIdUrl;
import static com.amazon.amazonpaymerchanturl.utils.URLCaseSensitivityConvertorUtil.getLowerCaseConvertedURL;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.constants.InvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationStatus;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.ManualUrlReview;
import com.amazon.amazonpaymerchanturl.model.TriggerURLReviewRequest;
import com.amazon.amazonpaymerchanturl.model.URLReviewRequest;
import com.amazon.amazonpaymerchanturl.model.URLReviewResponse;
import com.amazon.amazonpaymerchanturl.model.UrlValidationStatus;
import com.amazon.amazonpaymerchanturl.utils.StegoDBUrlUpdateUtil;
import com.amazon.amazonpaymerchanturl.utils.UrlStatusNotificationUtil;
import com.amazon.apurlvalidation.model.UrlResponse;
import com.amazon.apurlvalidation.constant.UrlStatus;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class URLValidationResultProcessor {

    private static final String AUTO_APPROVED = "AutoApproved";
    private static final String AUTO_DENIED = "AutoDenied";

    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final StegoDBUrlUpdateUtil stegoDBUrlUpdateUtil;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final UrlStatusNotificationUtil urlStatusNotificationUtil;
    private final String urlNotificationTopic;

    @Inject
    public URLValidationResultProcessor(final DomainValidationDDBAdapter domainValidationDDBAdapter,
                                        final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                        final StegoDBUrlUpdateUtil stegoDBUrlUpdateUtil,
                                        final CloudWatchMetricsHelper cloudWatchMetricsHelper,
                                        final UrlStatusNotificationUtil urlStatusNotificationUtil,
                                        final String urlNotificationTopic) {
        this.domainValidationDDBAdapter = domainValidationDDBAdapter;
        this.urlInvestigationDDBAdapter = urlInvestigationDDBAdapter;
        this.stegoDBUrlUpdateUtil = stegoDBUrlUpdateUtil;
        this.cloudWatchMetricsHelper = cloudWatchMetricsHelper;
        this.urlStatusNotificationUtil = urlStatusNotificationUtil;
        this.urlNotificationTopic = urlNotificationTopic;
    }

    public ManualUrlReview process(@NonNull final List<UrlResponse> urlStatusResponseList,
            @NonNull final TriggerURLReviewRequest triggerURLReviewRequest, final String urlType) {
        List<UrlValidationStatus> urlValidationStatusList = new ArrayList<>();
        urlStatusResponseList.forEach(urlStatusResponse ->
                urlValidationStatusList.add(computeURLValidationStatusAndUpdateStegoService(urlStatusResponse,
                        triggerURLReviewRequest, urlType)));
        return getManualUrlReview(urlValidationStatusList);
    }

    /**
     * process method to process Upfront validation results for a single URL.
     *
     * @param urlStatusResponse status of url from upfrontValidation list.
     * @param urlReviewRequest  url review request.
     * @param reviewStartTime start time for Upfront validation review.
     * @return URLReviewResponse response with Url SubInvestigation status.
     */
    public URLReviewResponse process(@NonNull final UrlResponse urlStatusResponse,
            @NonNull final URLReviewRequest urlReviewRequest, final long reviewStartTime) {
        SubInvestigationStatus status;
        switch (urlStatusResponse.getUrlStatus()) {
            case ALLOWED:
                status = SubInvestigationStatus.COMPLIANT;
                break;
            case DENIED:
                status = SubInvestigationStatus.NON_COMPLIANT;
                break;
            default:
                status = SubInvestigationStatus.UNKNOWN;
        }

        final long reviewEndTime = Instant.now().toEpochMilli();
        createUrlInvestigationDDBEntry(urlReviewRequest, status.getSubInvestigationStatus(),
                reviewStartTime, reviewEndTime);
        return URLReviewResponse.builder()
                .subInvestigationStatus(status.getSubInvestigationStatus())
                .subInvestigationType(SubInvestigationType.UPFRONT_VALIDATION.getSubInvestigationType())
                .reviewTime(reviewEndTime)
                .build();
    }

    private UrlValidationStatus computeURLValidationStatusAndUpdateStegoService(UrlResponse urlStatusResponse,
            TriggerURLReviewRequest triggerURLReviewRequest, String urlType) {
        String complianceStatus = null;
        String url = urlStatusResponse.getUrl();
        log.info("For Url {} : Url Status is {}", urlStatusResponse.getUrl(), urlStatusResponse.getUrlStatus());
        UrlValidationStatus urlValidationStatus = UrlValidationStatus.builder().url(url).urlStatus(UrlStatus.MANUAL)
                .sendResponseCode(SUCCESS_STATUS_CODE).build();
        boolean shouldUpdateStegoService = shouldUpdateStegoService(urlStatusResponse.getUrlStatus());

        if (UrlStatus.ALLOWED.equals(urlStatusResponse.getUrlStatus())) {
            complianceStatus = InvestigationStatus.COMPLIANT.getInvestigationStatus();
        } else if (UrlStatus.DENIED.equals(urlStatusResponse.getUrlStatus())) {
            complianceStatus = InvestigationStatus.NON_COMPLIANT.getInvestigationStatus();
        }

        if (shouldUpdateStegoService) {
            urlValidationStatus.setUrlStatus(urlStatusResponse.getUrlStatus());
            urlValidationStatus = createDDBEntryAndAddURLInStegoServiceApplication(url, triggerURLReviewRequest,
                    complianceStatus, urlValidationStatus, urlType);
            // Send notification for compliance status to SNS topic
            urlStatusNotificationUtil
                    .buildAndPublishURLReviewNotification(triggerURLReviewRequest.getClientReferenceGroupId(),
                            complianceStatus, url, Instant.now().toEpochMilli(), "", urlNotificationTopic);
        }

        cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(
                getUrlValidationStatusMetricMap().get(urlValidationStatus.getUrlStatus()));
        return urlValidationStatus;
    }

    private UrlValidationStatus createDDBEntryAndAddURLInStegoServiceApplication(String url,
            TriggerURLReviewRequest triggerURLReviewRequest, String investigationStatus,
            UrlValidationStatus urlValidationStatus, String urlType) {
        try {
            url = getLowerCaseConvertedURL(url);
            createDDBEntry(triggerURLReviewRequest.getClientReferenceGroupId(), investigationStatus,
                    triggerURLReviewRequest.getClientCustomInformation(),
                    triggerURLReviewRequest.getSource(), url, urlType);
            getAndUpdateStegoServiceApplication(url, triggerURLReviewRequest, investigationStatus, urlType);
            return urlValidationStatus;
        } catch (Exception exception) {
            log.info("Got an exception so return a url for manual Investigation Exception: {}",
                    exception.getMessage());
            return UrlValidationStatus.builder().url(urlValidationStatus.getUrl()).urlStatus(UrlStatus.MANUAL)
                    .sendResponseCode(FAILED_STATUS_CODE).build();
        }
    }

    private void getAndUpdateStegoServiceApplication(String url,
                                                     TriggerURLReviewRequest triggerURLReviewRequest,
                                                     String investigationStatus, String urlType) {
        stegoDBUrlUpdateUtil.getAndUpdateStegoServiceApplication(
                getLWAClientId(triggerURLReviewRequest.getClientCustomInformation()),
                url, investigationStatus, urlType);
        log.info("Successfully update the StegoService database for clientId_region: {} and url: {}",
                triggerURLReviewRequest.getClientCustomInformation(), url);
    }

    private ManualUrlReview getManualUrlReview(List<UrlValidationStatus> urlReviewedStatusList) {
        List<String> manualUrlReviewList = new ArrayList<>();
        urlReviewedStatusList.forEach(urlReviewedStatus -> {
            if (UrlStatus.MANUAL.equals(urlReviewedStatus.getUrlStatus())) {
                manualUrlReviewList.add(urlReviewedStatus.getUrl());
            }
        });
        return ManualUrlReview.builder().urlList(manualUrlReviewList).build();
    }

    private void createDDBEntry(final String clientReferenceGroupId, final String investigationStatus,
            final String clientCustomInformation, final String source, final String url, final String urlType) {
        final String reviewInfo = InvestigationStatus.COMPLIANT.getInvestigationStatus()
                .equals(investigationStatus) ? AUTO_APPROVED : AUTO_DENIED;
        final Long investigationTime = Instant.now().toEpochMilli();
        final AmazonPayDomainValidationItem ddbEntry = AmazonPayDomainValidationItem.builder()
                .url(url)
                .clientReferenceGroupId(clientReferenceGroupId)
                .clientInfo(getLWAClientId(clientCustomInformation))
                .urlSource(source)
                .investigationStatus(investigationStatus)
                .reviewInfo(reviewInfo)
                .caseCreationTime(investigationTime)
                .caseCompletionTime(investigationTime)
                .urlType(urlType)
                .build();
        domainValidationDDBAdapter.createOrUpdateEntry(ddbEntry);
        log.info("Successfully added entry in the dynamoDB Table for url: {}", url);
    }

    private boolean shouldUpdateStegoService(UrlStatus urlStatus) {
        return UrlStatus.ALLOWED.equals(urlStatus) || UrlStatus.DENIED.equals(urlStatus);
    }

    private void createUrlInvestigationDDBEntry(final URLReviewRequest urlReviewRequest, final String status,
                                                final long reviewStartTime, final long reviewEndTime) {
        final UrlInvestigationItem urlInvestigationItem = UrlInvestigationItem.builder()
                .investigationId(urlReviewRequest.getInvestigationId())
                .investigationType(urlReviewRequest.getInvestigationType())
                .subInvestigationType(SubInvestigationType.UPFRONT_VALIDATION.getSubInvestigationType())
                .clientReferenceGroupIdUrl(createClientReferenceGroupIdUrl(urlReviewRequest.getClientReferenceGroupId(),
                        urlReviewRequest.getReviewURL()))
                .subInvestigationStatus(status)
                .reviewStartTime(reviewStartTime)
                .reviewEndTime(reviewEndTime)
                .reviewStartDate(Instant.ofEpochMilli(reviewStartTime).truncatedTo(ChronoUnit.DAYS).toEpochMilli())
                .build();
        urlInvestigationDDBAdapter.createOrUpdateEntry(urlInvestigationItem);
        log.info("Successfully added entry in UrlInvestigation table for url: {}", urlReviewRequest.getReviewURL());
    }
}
