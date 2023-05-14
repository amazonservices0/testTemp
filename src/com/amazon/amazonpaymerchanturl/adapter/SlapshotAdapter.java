package com.amazon.amazonpaymerchanturl.adapter;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLDownstreamFailureException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.model.SlapshotContractFulfillmentRequest;
import com.amazon.amazonpaymerchanturl.utils.JSONObjectMapperUtil;
import com.amazon.slapshot.coral.CSSlapshotServiceClient;
import com.amazon.slapshot.coral.ContractFulfillmentRequest;
import com.amazon.slapshot.coral.Fulfillment;
import com.amazon.slapshot.coral.GetFulfillmentResponse;
import com.amazon.slapshot.coral.Snapshot;
import com.amazon.slapshot.coral.impl.FulfillContractCall;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

/**
 * Common class to get the response from Slapshot and adapt it to the internal models.
 */
@Log4j2
@RequiredArgsConstructor
public class SlapshotAdapter {

    @NonNull
    private final CSSlapshotServiceClient slapshotServiceClient;

    @NonNull
    private final JSONObjectMapperUtil jsonObjectMapperUtil;

    /**
     * Method to collect data from slapshot and adapt the slapshot response into the internal model of given type.
     *
     * @param request - slapshot request entity.
     * @param <T> - Generic Type for Internal Model
     * @param type - The expected Internal Model
     * @return T - Internal Model
     */
    public <T> T adapt(@NonNull final SlapshotContractFulfillmentRequest request, @NonNull final Class<T> type) {

        final GetFulfillmentResponse slapshotResponse;
        final String serializedData;

        log.info("SlapshotAdapter called with input parameters-ContractID : {} ,ResponseNode : {}, RequestArgs : {}",
                request.getContractId(), request.getResponseNode(), request.getRequestParams());

        final FulfillContractCall fulfillContractCall = slapshotServiceClient.newFulfillContractCall();
        final ContractFulfillmentRequest contractFulfillmentRequest = makeContractFullfillementRequest(request);

        try {
            slapshotResponse = fulfillContractCall.call(contractFulfillmentRequest);
        } catch (Exception e) {
            throw new AmazonPayMerchantURLDownstreamFailureException(String.format(
                    "Exception while calling SlapshotService for SlapshotContractFulfillmentRequest: %s ", request), e);
        }

        /*
        Learning from TT:0132250447. There are few instances where Slapshot contract didn't propagate exceptions in its
        response and has silently failed. This resulted in NPEs in the corresponding parsing logic which assumes certain
        fields to be present upon successful execution of Contract.
         */
        serializedData = Optional.ofNullable(slapshotResponse)
                .map(GetFulfillmentResponse::getFulfillment)
                .map(Fulfillment::getSnapshot)
                .map(Snapshot::getSerializedData)
                .orElseThrow(() -> new AmazonPayMerchantURLNonRetryableException(String.format(
                        "Null Response from SlapshotService for SlapshotContractFulfillmentRequest : %s .", request)));

        //Deserialize the slapshot data and return as internal model
        return jsonObjectMapperUtil.deserializeSlapshotData(serializedData, request.getResponseNode(), type);
    }

    private ContractFulfillmentRequest makeContractFullfillementRequest(
                                                                    final SlapshotContractFulfillmentRequest request) {
        final ContractFulfillmentRequest contractFulfillmentRequest = new ContractFulfillmentRequest();
        contractFulfillmentRequest.setContractId(request.getContractId());
        contractFulfillmentRequest.setProjectName(request.getProjectName());
        contractFulfillmentRequest.setFulfillmentArguments(request.getRequestParams());
        return contractFulfillmentRequest;
    }
}
