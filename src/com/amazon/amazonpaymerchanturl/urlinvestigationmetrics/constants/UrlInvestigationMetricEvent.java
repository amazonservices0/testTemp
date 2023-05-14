package com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.constants;

import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Map;

import static com.amazon.amazonpaymerchanturl.constants.InvestigationType.NEW_INVESTIGATION;
import static com.amazon.amazonpaymerchanturl.constants.InvestigationType.PERIODIC_INVESTIGATION;

/**
 * This enum contains the time duration values required for an investigation type
 */
@Getter
@RequiredArgsConstructor
public enum UrlInvestigationMetricEvent {

    AUTO_LIGHTWEIGHT(SubInvestigationType.AUTO_LIGHTWEIGHT.getSubInvestigationType(),
            Duration.ofHours(4), Duration.ofHours(1).plusMinutes(10), null,
            //This metric Category should be in descending order based on Duration values
            ImmutableMap.of(
                    "LightWeightThreeHoursDelay", Duration.ofHours(3).toMillis(),
                    "LightWeightTwoHoursDelay", Duration.ofHours(2).toMillis(),
                    "LightWeightOneHourDelay", Duration.ofHours(1).toMillis())),

    AUTO_HEAVYWEIGHT(SubInvestigationType.AUTO_HEAVYWEIGHT.getSubInvestigationType(),
            Duration.ofDays(7), Duration.ofDays(3).plusMinutes(10), null,
            //This metric Category should be in descending order based on Duration values
            ImmutableMap.of(
                    "HeavyWeightFiveOrMoreDaysDelay", Duration.ofDays(5).toMillis(),
                    "HeavyWeightFourDaysDelay", Duration.ofDays(4).toMillis(),
                    "HeavyWeightThreeDaysDelay", Duration.ofDays(3).toMillis())),

    MANUAL_NEW_INVESTIGATION(SubInvestigationType.MANUAL.getSubInvestigationType(),
            Duration.ofDays(21), Duration.ofDays(10).plusMinutes(10), NEW_INVESTIGATION.getInvestigationType(),
            //This metric Category should be in descending order based on Duration values
            ImmutableMap.of(
                    "Manual_NewInvestigation_20_days_Delay", Duration.ofDays(20).toMillis(),
                    "Manual_NewInvestigation_10_days_Delay", Duration.ofDays(10).toMillis())),

    MANUAL_INVESTIGATION_MONITORING_TYPE(SubInvestigationType.MANUAL.getSubInvestigationType(),
            Duration.ofDays(11), Duration.ofDays(20).plusMinutes(10), PERIODIC_INVESTIGATION.getInvestigationType(),
            //This metric Category should be in descending order based on Duration values
            ImmutableMap.of(
                    "Manual_Investigation_MonitoringType_MoreThan_20days_Delay", Duration.ofDays(20).toMillis()));

    /**
     * This will the subInvestigationType received to emit the metrics
     */
    private final String subInvestigationType;

    /**
     * Data will be queried from DB for this amount of period
     */
    private final Duration dataPeriodQueried;

    /**
     * This is the threshold will be keeping to query the records from DB
     * If thresholdForDelay is 1 day , then will be fetching records before one day from DB
     */
    private final Duration minThresholdForDelay;

    /**
     * This defines whether the investigation is New_Investigation or Periodic_Investigation
     */
    private final String investigationType;

    /**
     * These are the categories that items will be grouped based on the SLA delay time
     * and emit the metrics for each category
     * NOTE : This metric Category should be in descending order based on Duration values
     */
    private final Map<String, Long> metricCategories;

    private static final String MANUAL_NEWINVESTIGATION = "Manual_NewInvestigation";
    private static final String MANUAL_MONITORINGTYPE = "ManualInvestigation_MonitoringType";

    @JsonCreator
    public static UrlInvestigationMetricEvent readValue(
            @JsonProperty(value = "scanType", required = true) String scanType) {
        if (scanType.equals(SubInvestigationType.AUTO_LIGHTWEIGHT.getSubInvestigationType())) {
            return AUTO_LIGHTWEIGHT;
        } else if (scanType.equals(SubInvestigationType.AUTO_HEAVYWEIGHT.getSubInvestigationType())) {
            return AUTO_HEAVYWEIGHT;
        } else if (scanType.equals(MANUAL_NEWINVESTIGATION)) {
            return MANUAL_NEW_INVESTIGATION;
        } else if (scanType.equals(MANUAL_MONITORINGTYPE)) {
            return MANUAL_INVESTIGATION_MONITORING_TYPE;
        } else {
            throw new AmazonPayMerchantURLNonRetryableException("Invalid ScanType is received " + scanType);
        }
    }
}
