package com.amazon.meridianservice.helper;

import com.amazon.meridianservice.model.common.RefreshBatch;
import com.amazon.meridianservice.model.common.WorkflowType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
This is a Helper class to help RefreshDriverHandler with business logic.
 **/
@Log4j2
public final class RefreshDriverHelper {

    private static final int MERCHANT_BATCH_SIZE = 2000;

    private RefreshDriverHelper() {

    }

    /**
     * This method will take the activeMerchants and onboardedMerchants list and.
     * classify them into different categories based on workflowType
     * MoreInfo - Maps classification done using Maps.difference
     * <a href="https://guava.dev/releases/3.0/api/docs/com/google/common/collect/MapDifference.html">link</a>
     * @param activeMerchants activeMerchants that need to onboarded/updated in System
     * @param onboardedMerchants existing onboarded merchants in system
     * @param refreshWorkflowSettings config for the Auto-Refresh workflow
     * @return Map of merchantsToBeProcessed with
     *          Key - contains WorkflowType(Onboard/Update/Offboard)
     *          Value - Map<String, List<String>> marketplaceIdMerchantIdsMap
     */
    public static Map<WorkflowType, Map<String, List<String>>> getClassifiedMerchantsWithWorkflowType(
            Map<String, List<String>> activeMerchants, Map<String, List<String>> onboardedMerchants,
            Map<String, String> refreshWorkflowSettings) {

        MapDifference<String, List<String>> activeMerchantsDelta = Maps.difference(activeMerchants,
                onboardedMerchants);

        Map<WorkflowType, Map<String, List<String>>> merchantsTobeProcessed = new HashMap<>();
        merchantsTobeProcessed.put(WorkflowType.ONBOARD, new HashMap<>(activeMerchantsDelta.entriesOnlyOnLeft()));
        merchantsTobeProcessed.put(WorkflowType.OFFBOARD, new HashMap<>(activeMerchantsDelta.entriesOnlyOnRight()));
        merchantsTobeProcessed.put(WorkflowType.UPDATE, new HashMap<>(activeMerchantsDelta.entriesInCommon()));

        activeMerchantsDelta.entriesDiffering().keySet().forEach(marketplaceId -> {

            //CollectionUtils.subtract() - gives the new collection by subtracting objects of one collection from other.
            //Get MerchantsToBeOffboarded - by subtracting ActiveMerchants from OnboardedMerchants
            List<String> list = (List<String>) CollectionUtils.subtract(
                    activeMerchantsDelta.entriesDiffering().get(marketplaceId).rightValue(),
                    activeMerchantsDelta.entriesDiffering().get(marketplaceId).leftValue());

            mergeToMerchantsToBeProcessedMap(marketplaceId, WorkflowType.OFFBOARD, merchantsTobeProcessed, list);

            //Get MerchantsToBeOnboarded - by subtracting OnboardedMerchants from ActiveMerchants
            list = (List<String>) CollectionUtils.subtract(
                    activeMerchantsDelta.entriesDiffering().get(marketplaceId).leftValue(),
                    activeMerchantsDelta.entriesDiffering().get(marketplaceId).rightValue());

            mergeToMerchantsToBeProcessedMap(marketplaceId, WorkflowType.ONBOARD, merchantsTobeProcessed, list);

            //Collections.intersection() - Provides the common elements between two collections
            ///Get MerchantsToBeUpdated - from the intersection of ActiveMerchants & OnboardedMerchants
            list = (List<String>) CollectionUtils.intersection(
                    activeMerchantsDelta.entriesDiffering().get(marketplaceId).leftValue(),
                    activeMerchantsDelta.entriesDiffering().get(marketplaceId).rightValue());
            mergeToMerchantsToBeProcessedMap(marketplaceId, WorkflowType.UPDATE, merchantsTobeProcessed, list);
        });

        //Remove entry from Map if specific workflow Config is disabled or MerchantsToBeProcessed is empty
        merchantsTobeProcessed.entrySet().removeIf(entrySet ->
                (!Boolean.parseBoolean(refreshWorkflowSettings.get(entrySet.getKey().name())))
                        || (entrySet.getValue().isEmpty()));

        return merchantsTobeProcessed;
    }

    private static void mergeToMerchantsToBeProcessedMap(String marketplaceId, WorkflowType workflowType,
                                                  Map<WorkflowType, Map<String, List<String>>> merchantsTobeProcessed,
                                                  List<String> merchantIds) {
        if (!CollectionUtils.isEmpty(merchantIds)) {
            merchantsTobeProcessed.get(workflowType)
                    .computeIfAbsent(marketplaceId, v -> new ArrayList<>()).addAll(merchantIds);
        }
    }

    /**
     * This method will fetch max.2k merchants from one workflowType in given merchantsToBeProcessedMap.
     *
     * @param merchantsTobeProcessed Map - with WorkflowType as Key & MarketplaceIdMerchantIdsMap as Value
     * @return RefreshBatch which contains Max-2K MerchantsToBeProcessed in Next batch with WorkflowType
     * and  PendingMerchantsToBeRefreshed
     *
     * Additional Info:
     * The batching will be done using MultiMap - A Multimap can store more than one value against a key.
     * Both the keys and the values are stored in a collection,
     * and considered to be alternates for Map<K, List<V>> or Map<K, Set<V>>
     *
     * Link : https://guava.dev/releases/19.0/api/docs/com/google/common/collect/Multimap.html
     */
    public static RefreshBatch getMerchantBatch(
            Map<WorkflowType, Map<String, List<String>>> merchantsTobeProcessed) {

        if (MapUtils.isEmpty(merchantsTobeProcessed)) {
            return RefreshBatch.builder()
                    .marketplaceIdMerchantIdsMap(Collections.emptyMap())
                    .pendingMerchantsToBeProcessed(Collections.emptyMap()).build();
        }

        WorkflowType workflowType = getFirstWorkflowTypeFromMap(merchantsTobeProcessed);

        //Create a MultiMap from Java Map<String, List<String>>
        ListMultimap<String, String> merchantsToBeProcessedForSelectedWorkflow
                = merchantsTobeProcessed.get(workflowType).entrySet().stream().
                collect(Multimaps.flatteningToMultimap(Map.Entry::getKey,
                        entrySet -> entrySet.getValue().stream(), ArrayListMultimap::create));

        ListMultimap<String, String> merchantBatchTobeProcessed = ArrayListMultimap.create();

        //Iterate over merchantsToBeProcessedMap and fetch specific number of merchants that fit for one Batch
        merchantsToBeProcessedForSelectedWorkflow.entries().stream().limit(MERCHANT_BATCH_SIZE).forEach(entry -> {
            merchantBatchTobeProcessed.put(entry.getKey(), entry.getValue());
        });

        //Removed the items(which are fetched for next batch) from merchantsToBeProcessedMap
        merchantBatchTobeProcessed.entries().forEach(
                entry -> merchantsToBeProcessedForSelectedWorkflow.remove(entry.getKey(), entry.getValue()));

        //convert merchantsToBeProcessedMap (which have pending merchants that need to be processed) to Java Map
        Map<WorkflowType, Map<String, List<String>>> pendingMerchantsToBeProcessed
                = new HashMap<>(merchantsTobeProcessed);
        pendingMerchantsToBeProcessed.put(workflowType, Multimaps.asMap(merchantsToBeProcessedForSelectedWorkflow));

        //Remove any entries if the values are empty (In case, If all the merchants in a workflow are processed)
        pendingMerchantsToBeProcessed.values().removeIf(entrySet -> entrySet.values().isEmpty());

        log.info("WorkflowType - {}. Merchant counts :: Total Pending : {} - To be processed in next batch : {}",
                workflowType, merchantsToBeProcessedForSelectedWorkflow.values().size(),
                merchantBatchTobeProcessed.values().size());

        return RefreshBatch.builder()
                .workflowType(workflowType)
                .marketplaceIdMerchantIdsMap(Multimaps.asMap(merchantBatchTobeProcessed))
                .pendingMerchantsToBeProcessed(pendingMerchantsToBeProcessed)
                .build();
    }

    private static WorkflowType getFirstWorkflowTypeFromMap(
            Map<WorkflowType, Map<String, List<String>>> workflowTypeMerchantIdMap) {
        return workflowTypeMerchantIdMap.entrySet().iterator().next().getKey();
    }
}
