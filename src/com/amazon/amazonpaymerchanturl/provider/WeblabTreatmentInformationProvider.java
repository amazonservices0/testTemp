package com.amazon.amazonpaymerchanturl.provider;

import com.amazon.amazonpaymerchanturl.adapter.SlapshotAdapter;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.SlapshotContractFulfillmentRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bouncycastle.util.Arrays;

import java.util.Map;

import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.WEBLAB_TREATMENT_FETCH_FAILED;

/**
 * Class to get  Weblab Treatments
 *
 */
@Log4j2
@RequiredArgsConstructor
public class WeblabTreatmentInformationProvider {

    //Slapshot Input Contract Attributes
    private static final String WEBLAB_NAME_ATTRIBUTE = "weblabName";
    private static final String MERCHANT_ID_ATTRIBUTE = "customerId";
    private static final String MARKETPLACE_ID_ATTRIBUTE = "marketplaceId";

    private static final String SLAPSHOT_CONTRACT_ID = "APDomainValidation_WeblabContract";
    private static final String RESPONSE_NODE = "WeblabServiceNode";
    private static final String SLAPSHOT_CONTRACT_PROJECT = "GLOBAL";

    private static final String EXCEPTION_MSG =
            "Response for retrieving the weblabTreatment for merchantId - %s is null or Empty";
    private static final String TREATMENT_C = "C";
    private static final String TREATMENT_ATTRIBUTE = "treatment";

    @NonNull
    private final SlapshotAdapter slapshotAdapter;

    @NonNull
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;

    /**
     * Method to get  weblab treatment for Merchants.
     * @param merchantId merchant ID
     * @param marketplaceId Market Place ID of a merchant
     * @param weblabName Weblab expreriment name
     * @return treatment (C,T1,T2.. etc)
     */
    public String getWebLabTreatmentInformation(@NonNull String merchantId,
                                                @NonNull String marketplaceId,
                                                @NonNull String weblabName) {
        log.info("getWebLabTreatmentInformation called for merchantId : " + merchantId);
        final Map<String, String> requestParams = new ImmutableMap.Builder<String, String>()
                .put(WEBLAB_NAME_ATTRIBUTE, weblabName)
                .put(MERCHANT_ID_ATTRIBUTE, merchantId)
                .put(MARKETPLACE_ID_ATTRIBUTE, marketplaceId)
                .build();

        final SlapshotContractFulfillmentRequest slapshotRequest =
                SlapshotContractFulfillmentRequest.builder()
                        .contractId(SLAPSHOT_CONTRACT_ID)
                        .responseNode(RESPONSE_NODE)
                        .projectName(SLAPSHOT_CONTRACT_PROJECT)
                        .requestParams(requestParams)
                        .build();
        try {
            final ObjectNode[] treatments = slapshotAdapter.adapt(slapshotRequest, ObjectNode[].class);
            if (Arrays.isNullOrEmpty(treatments)) {
                throw new AmazonPayMerchantURLNonRetryableException(String.format(EXCEPTION_MSG, merchantId));
            }
            /*
            Slapshot returns the data elements in the form of array
            WeblabResponse Node will have one Data Item in the array Ex : [{"treatment":"Value"}]
            Hence, We will consider only 0th (zero) index for value and there will be no more than one index
             */
            final String treatment = treatments[0].get(TREATMENT_ATTRIBUTE).textValue();
            log.info("Received Weblab - {} Treatment for MerchantID : {} --> {}", weblabName, merchantId, treatment);
            return treatment;
        } catch (AmazonPayMerchantURLNonRetryableException e) {
            log.error("Null or Empty Weblab Treatments Received from SlapshotAdapter , returning C."
                            + "weblabName: {} merchantId: {} marketplaceId: {} : {}",
                    weblabName, merchantId, marketplaceId, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected Error while fetching treatment, returning C."
                            + "weblabName: {} merchantId: {} marketplaceId: {} : {}",
                    weblabName, merchantId, marketplaceId, e.getMessage());
        }
        cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(WEBLAB_TREATMENT_FETCH_FAILED);
        return TREATMENT_C;
    }
}
