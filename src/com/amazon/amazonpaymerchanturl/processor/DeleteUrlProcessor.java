package com.amazon.amazonpaymerchanturl.processor;

import com.amazon.amazonpaydomainvalidationdao.adapter.DomainValidationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.AmazonPayDomainValidationItem;
import com.amazon.amazonpaymerchanturl.constants.ActiveStatus;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.utils.UrlStatusNotificationUtil;
import com.amazon.urlvendorreviewlib.component.DaggerUrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.component.UrlVendorReviewLibComponent;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewClientException;
import com.amazon.urlvendorreviewlib.exceptions.UrlVendorReviewServerException;
import com.amazon.urlvendorreviewlib.factory.VendorDeboardUrlHandlerFactory;
import com.amazon.urlvendorreviewmodel.request.VendorDeboardUrlRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpStatus;

import javax.inject.Inject;
import java.time.Instant;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.PROCESS_DELETE_URL_FAILURE_METRICS_SERVER_ERROR;
import static com.amazon.amazonpaymerchanturl.constants.VendorReviewConstants.EVERCOMPLIANT_ID;

@RequiredArgsConstructor
@Log4j2
public class DeleteUrlProcessor {

    private final DomainValidationDDBAdapter domainValidationDDBAdapter;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final UrlStatusNotificationUtil urlStatusNotificationUtil;
    private final UrlVendorReviewLibComponent urlVendorReviewLibComponent;
    private final VendorDeboardUrlHandlerFactory vendorDeboardUrlHandlerFactory;
    private final String urlStatusNotificationTopic;

    @Inject
    public DeleteUrlProcessor(final DomainValidationDDBAdapter domainValidationDDBAdapter,
                              final CloudWatchMetricsHelper cloudWatchMetricsHelper,
                              final UrlStatusNotificationUtil urlStatusNotificationUtil,
                              final String urlStatusNotificationTopic) {
        this.domainValidationDDBAdapter = domainValidationDDBAdapter;
        this.cloudWatchMetricsHelper = cloudWatchMetricsHelper;
        this.urlStatusNotificationUtil = urlStatusNotificationUtil;
        this.urlStatusNotificationTopic = urlStatusNotificationTopic;
        urlVendorReviewLibComponent = DaggerUrlVendorReviewLibComponent.create();
        vendorDeboardUrlHandlerFactory = urlVendorReviewLibComponent.getVendorDeboardUrlHandlerFactory();
    }

    public void process(@NonNull final String clientReferenceGroupId, @NonNull final String url,
                        final boolean isVendorDeboardRequired, @NonNull final String deboardType) {
        final AmazonPayDomainValidationItem newDDbItem = AmazonPayDomainValidationItem.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .url(url)
                .isActive(Boolean.FALSE)
                .deletionTime(Instant.now().toEpochMilli())
                .build();

        try {
            if (isVendorDeboardRequired) {
                deboardUrl(clientReferenceGroupId, url, deboardType);
            }
            domainValidationDDBAdapter.updateEntry(newDDbItem);
            urlStatusNotificationUtil.buildAndPublishDeleteUrlNotification(clientReferenceGroupId, url,
                    ActiveStatus.DISABLED.getValue(), urlStatusNotificationTopic);
        } catch (AmazonPayDomainValidationDAONonRetryableException | UrlVendorReviewClientException e) {
            final String msg = String.format("Non-retryable exception received. while deleting the url: %s for "
                            + "clientReferenceGroupId: %s", url, clientReferenceGroupId);
            throw new AmazonPayMerchantURLNonRetryableException(msg, e, HttpStatus.SC_BAD_REQUEST);
        } catch (AmazonPayDomainValidationDAORetryableException | UrlVendorReviewServerException e) {
            cloudWatchMetricsHelper
                    .publishRecordCountMetricToCloudWatch(PROCESS_DELETE_URL_FAILURE_METRICS_SERVER_ERROR);
            final String msg = String.format("Retryable exception received. while deleting the url: %s for "
                            + "clientReferenceGroupId: %s", url, clientReferenceGroupId);
            throw new AmazonPayMerchantURLRetryableException(msg, e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void deboardUrl(final String clientReferenceGroupId, final String url, final String deboardType) {
        log.info("initiating call to everC to deboard the url: {} for clientReferenceGroupId: {}",
                url, clientReferenceGroupId);

        final VendorDeboardUrlRequest vendorDeboardUrlRequest = VendorDeboardUrlRequest.builder()
                .clientReferenceGroupId(clientReferenceGroupId)
                .reviewURL(url)
                .subInvestigationType(deboardType)
                .build();
        vendorDeboardUrlHandlerFactory.produceVendorHandler(EVERCOMPLIANT_ID)
                .deboardUrl(vendorDeboardUrlRequest);
    }
}
