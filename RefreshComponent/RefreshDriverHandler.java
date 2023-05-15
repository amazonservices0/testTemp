package com.amazon.meridianservice.handler;

import com.amazon.meridianservice.adapters.S3Adapter;
import com.amazon.meridianservice.exceptions.MeridianServiceEntityNotFoundException;
import com.amazon.meridianservice.helper.RefreshDriverHelper;
import com.amazon.meridianservice.model.common.RefreshBatch;
import com.amazon.meridianservice.model.common.WorkflowType;
import com.amazon.meridianservice.model.lambda.RefreshDriver.RefreshDriverRequest;
import com.amazon.meridianservice.model.lambda.RefreshDriver.RefreshDriverResponse;
import com.amazon.meridianservice.model.lambda.RefreshDriver.Status;
import com.amazon.meridianservice.model.common.MerchantIdentifier;
import com.amazon.meridianservice.translator.S3DataTranslator;
import com.amazon.meridianservice.utils.S3Util;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.amazon.meridianservice.Constants.ONBOARDED_MERCHANT_INFO_FILE_NAME;
import static com.amazon.meridianservice.constants.ModuleConstants.ONBOARDED_MERCHANT_INFO_S3_BUCKET;
import static com.amazon.meridianservice.constants.ModuleConstants.REFRESH_DRIVER_PROCESSING_S3_BUCKET;
import static com.amazon.meridianservice.constants.ModuleConstants.REFRESH_WORKFLOW_SETTINGS;
import static com.amazon.meridianservice.translator.MerchantIdentifierTranslator.getMarketplaceIdMerchantIdMap;
import static com.amazon.meridianservice.translator.MerchantMarketplaceIdTranslator.getMarketplaceIdMerchantIdsMap;
import static com.amazon.meridianservice.translator.MerchantMarketplaceIdTranslator.getMerchantIdentifiers;

@Log4j2
@SuppressFBWarnings(value = {"EI_EXPOSE_REP2"})
public class RefreshDriverHandler extends AbstractLambdaHandler<RefreshDriverRequest,
        RefreshDriverResponse> {

    private static final String ACTIVE_MERCHANT_DATA_SPLITTER = "\n";

    private static final String TEMP_FOLDER_FOR_PROCESSING = "temp";

    private static final String FOLDER_FOR_FAILURE_FILE = "failed";
    private final S3Adapter s3Adapter;

    private final String refreshDriverProcessingBucket;

    private final String onboardedMerchantInfoBucket;

    private final Map<String, String> refreshWorkflowSettings;

    @Inject
    public RefreshDriverHandler(@NonNull final ObjectMapper objectMapper, @NonNull final S3Adapter s3Adapter,
                                @Named(REFRESH_WORKFLOW_SETTINGS) final Map<String, String> refreshWorkflowSettings,
                                @Named(REFRESH_DRIVER_PROCESSING_S3_BUCKET) final String refreshDriverProcessingBucket,
                                @Named(ONBOARDED_MERCHANT_INFO_S3_BUCKET) final String onboardedMerchantInfoBucket) {

        super(objectMapper, RefreshDriverRequest.class, RefreshDriverResponse.class);
        this.s3Adapter = s3Adapter;
        this.refreshDriverProcessingBucket = refreshDriverProcessingBucket;
        this.onboardedMerchantInfoBucket = onboardedMerchantInfoBucket;
        this.refreshWorkflowSettings = refreshWorkflowSettings;
    }

    @Override
    public RefreshDriverResponse handleRequest(RefreshDriverRequest input, Context context) throws IOException {

        log.info("Refresh driver called with input : {}", input);

        return Objects.isNull(input.getStatus())
                ? initiateRefresh(input)
                : continueRefresh(input);
    }

    private RefreshDriverResponse initiateRefresh(RefreshDriverRequest input) {

        log.info("Initiating merchant data refresh...");

        Map<WorkflowType, Map<String, List<String>>> merchantsToBeProcessed =
                getMerchantsAndActionsToBeProcessed(input.getInputFile());

        String pendingMerchantsInputFilePath = S3Util.getS3Uri(refreshDriverProcessingBucket,
                String.format("%s/%s", TEMP_FOLDER_FOR_PROCESSING, UUID.randomUUID()));

        log.info("File used for processing pending merchants : {}", pendingMerchantsInputFilePath);

        String failureFilePath = S3Util.getS3Uri(refreshDriverProcessingBucket,
                String.format("%s/%s", FOLDER_FOR_FAILURE_FILE, Instant.now().toString()));

        log.info("Failure file : {}", failureFilePath);

        RefreshBatch merchantsActiveBatch =
                RefreshDriverHelper.getMerchantBatch(merchantsToBeProcessed);

        s3Adapter.putObject(pendingMerchantsInputFilePath, merchantsActiveBatch.getPendingMerchantsToBeProcessed());

        return RefreshDriverResponse.builder()
                .failureFile(failureFilePath)
                .inputFile(pendingMerchantsInputFilePath)
                .marketplaceIdMerchantIdsMap(merchantsActiveBatch.getMarketplaceIdMerchantIdsMap())
                .status(getStatus(Collections.emptyMap(), null,
                        merchantsActiveBatch.getMarketplaceIdMerchantIdsMap()))
                .workflowType(merchantsActiveBatch.getWorkflowType())
                .build();
    }

    private RefreshDriverResponse continueRefresh(RefreshDriverRequest input) {

        Map<String, List<String>> failedMerchantsMap = input.getFailedMarketplaceIdMerchantIdsMap();
        updateFailureFile(failedMerchantsMap, input.getFailureFile());

        Map<WorkflowType, Map<String, List<String>>> merchantsToBeRefreshed = s3Adapter.getObject(
                input.getInputFile(), new TypeReference<Map<WorkflowType, Map<String, List<String>>>>() { });

        RefreshBatch merchantsActiveBatch = RefreshDriverHelper.getMerchantBatch(
                merchantsToBeRefreshed);
        s3Adapter.putObject(input.getInputFile(), merchantsActiveBatch.getPendingMerchantsToBeProcessed());
        return RefreshDriverResponse.builder()
                .failureFile(input.getFailureFile())
                .inputFile(input.getInputFile())
                .marketplaceIdMerchantIdsMap(merchantsActiveBatch.getMarketplaceIdMerchantIdsMap())
                .status(getStatus(failedMerchantsMap, input.getStatus(),
                        merchantsActiveBatch.getMarketplaceIdMerchantIdsMap()))
                .workflowType(merchantsActiveBatch.getWorkflowType())
                .build();
    }

    /**
     * This method fetch the active Merchants list and classify them different categories and return a map.
     * @param inputFilePath s3 path of active merchants
     * @return Map with Key as Workflow type (Update/Onboard/Offboard) and
     * value as Map<String, List<String>> which is MarketplaceIdMerchantIdsMap
     */
    private Map<WorkflowType, Map<String, List<String>>> getMerchantsAndActionsToBeProcessed(String inputFilePath) {
        String activeMerchantData = s3Adapter.getRecords(inputFilePath);
        Set<String> activeMerchantIdentifierSet = S3DataTranslator.getActiveMerchantsOrFailedMerchantsFromS3(
                activeMerchantData);

        Map<String, List<String>> activeMerchants = getMarketplaceIdMerchantIdsMap(activeMerchantIdentifierSet);

        String onboardedMerchantsOnPlatformS3Path = S3Util.getS3Uri(onboardedMerchantInfoBucket,
                ONBOARDED_MERCHANT_INFO_FILE_NAME);

        Map<String, List<String>>  onboardedMerchants = getMarketplaceIdMerchantIdMap(s3Adapter.getObject(
                onboardedMerchantsOnPlatformS3Path, new TypeReference<ArrayList<MerchantIdentifier>>() { }));

        //Get Merchants to be processed in Map Type where Key is WorkflowType & Value is MarketplaceIdMerchantIdsMap
        Map<WorkflowType, Map<String, List<String>>> merchantsToBeProcessed =
                RefreshDriverHelper.getClassifiedMerchantsWithWorkflowType(activeMerchants,
                        onboardedMerchants, refreshWorkflowSettings);
        log.info("merchantsToBeProcessed - Total  workflows: {}", merchantsToBeProcessed.values().size());

        return merchantsToBeProcessed;
    }

    private Status getStatus(Map<String, List<String>> failedMerchants, Status previousStatus,
                             Map<String, List<String>> marketplaceIdMerchantIdsMap) {
        boolean hasFailuresInPrevBatch = MapUtils.isNotEmpty(failedMerchants);
        boolean isTerminalState = MapUtils.isEmpty(marketplaceIdMerchantIdsMap);

        boolean hasFailures = (hasFailuresInPrevBatch
                || Status.IN_PROGRESS_WITH_PARTIAL_FAILURE.equals(previousStatus));

        return isTerminalState
                ? (hasFailures ? Status.FAILURE : Status.SUCCESS)
                : (hasFailures ? Status.IN_PROGRESS_WITH_PARTIAL_FAILURE : Status.IN_PROGRESS);
    }

    private void updateFailureFile(Map<String, List<String>> failedMerchants, String failureFilePath) {

        if (MapUtils.isEmpty(failedMerchants)) {
            return;
        }

        Set<String> s3FailedMerchantsList =  new HashSet<>();
        try {
            String failureMerchantData = s3Adapter.getRecords(failureFilePath);
            s3FailedMerchantsList = S3DataTranslator.getActiveMerchantsOrFailedMerchantsFromS3(failureMerchantData);
        } catch (MeridianServiceEntityNotFoundException e) {
            log.info("No existing failure file found with name {}", failureFilePath);
        }
        s3FailedMerchantsList.addAll(getMerchantIdentifiers(failedMerchants));

        log.info("Updating failure list - Count of total failed : {}", failedMerchants.size());

        String failureMerchantData = S3DataTranslator.putFailedMerchantsListToS3(s3FailedMerchantsList);
        s3Adapter.putString(failureFilePath, failureMerchantData);
    }
}
