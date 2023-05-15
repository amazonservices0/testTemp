package com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.processor;

import com.amazon.amazonpaydomainvalidationdao.adapter.UrlInvestigationDDBAdapter;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.constants.UrlInvestigationMetricEvent;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.model.TimeRange;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.utils.MetricUtil;
import com.amazon.amazonpaymerchanturl.utils.HandlersUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import javax.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.amazon.amazonpaymerchanturl.constants.SubInvestigationStatus.IN_REVIEW;

/**
 * This class handles teh business logic for fetching the items whose callbacks are pending and crossed SLA
 * and categorizes the items based on the required SLA breach types
 */
@Log4j2
public class UrlInvestigationMetricProcessor {

    private final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter;
    private final Clock systemClock;

    @Inject
    public UrlInvestigationMetricProcessor(final UrlInvestigationDDBAdapter urlInvestigationDDBAdapter,
                                           final Clock systemClock) {
        this.urlInvestigationDDBAdapter = urlInvestigationDDBAdapter;
        this.systemClock = systemClock;
    }

    public Map<String, List<UrlInvestigationItem>> process(final UrlInvestigationMetricEvent investigationMetricEvent) {
        List<UrlInvestigationItem> itemsCrossedSLA = getDelayedItemsForInvType(investigationMetricEvent);

        log.info("{} items identified which have SLA breaches", itemsCrossedSLA.size());
        return categorizeItemsForDelayedResponse(investigationMetricEvent, itemsCrossedSLA);
    }

    /**
     * Get all the items that are waiting for Investigation responses that crossed SLA from DB
     * @param scanEvent UrlInvestigationMetricEvent
     * @return List of UrlInvestigationItems
     */
    private List<UrlInvestigationItem> getDelayedItemsForInvType(final UrlInvestigationMetricEvent scanEvent) {

        final Long endTime = systemClock.instant().toEpochMilli() - scanEvent.getMinThresholdForDelay().toMillis();
        final Long startTime = endTime - scanEvent.getDataPeriodQueried().toMillis();

        List<TimeRange> queryListForTimeRange = MetricUtil.getQueryListForTimeRange(startTime, endTime);
        List<UrlInvestigationItem> urlInvestigationItemList = queryListForTimeRange.stream().flatMap(timeRange ->
                        urlInvestigationDDBAdapter.queryOnRStartDateAsSecondaryIndex(
                                timeRange.getStartDate(), scanEvent.getSubInvestigationType(),
                                scanEvent.getInvestigationType(),
                                timeRange.getStartTime(), timeRange.getEndTime()).stream())
                .collect(Collectors.toList());

        log.info("Number of items returned from DB : {}", urlInvestigationItemList.size());

        //we need to identify the items waiting for investigation response
        return urlInvestigationItemList.stream()
                .filter(urlInvestigationItem -> Objects.equals(urlInvestigationItem.getSubInvestigationStatus(),
                        IN_REVIEW.getSubInvestigationStatus())
                        && UrlValidator.getInstance()
                        .isValid(HandlersUtil.getUrl(urlInvestigationItem.getClientReferenceGroupIdUrl())))
                .collect(Collectors.toList());
    }

    /**
     * This method will group the items into categories that are waiting for investigation response that breaches SLA
     * For Ex : Map with "OneHourDelay" as key will contain items that are waiting for response more than one hour
     * @param urlInvestigationItemList items list  which haven't received investigation response
     * @return Map with Metric Category  as Key and investigation items list as value
     */
    private Map<String, List<UrlInvestigationItem>> categorizeItemsForDelayedResponse(
            UrlInvestigationMetricEvent urlInvestigationMetricEvent,
            List<UrlInvestigationItem> urlInvestigationItemList) {

        Map<String, List<UrlInvestigationItem>> categorizedItems = new HashMap<>();

        final Long currentTime = systemClock.instant().toEpochMilli();

        urlInvestigationItemList.stream().forEach((invItem -> {
            Long durationBetweenTimeStamps = MetricUtil.getBusinessDaysDuration(invItem.getReviewStartTime(),
                    currentTime, urlInvestigationMetricEvent);

            //The categorization will be based on the order of metricCategories in UrlInvestigationMetricEvent Enum
            String metricCategory = urlInvestigationMetricEvent.getMetricCategories().entrySet().stream()
                    .filter(category -> durationBetweenTimeStamps > category.getValue())
                    .map(Map.Entry::getKey).findFirst().orElse(null);

            if (StringUtils.isNotEmpty(metricCategory)) {
                categorizedItems.computeIfAbsent(metricCategory, v -> new ArrayList<>()).add(invItem);
            } else {
                log.info("Item not found in any Metric Category : {}", invItem);
            }
        }));
        return categorizedItems;
    }
}
