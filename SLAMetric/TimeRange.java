package com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Pojo for the Time attributes that need to Query the UrlInvestigation Table using partition and Sort keys
 */
@Data
@AllArgsConstructor
public class TimeRange {

    /**
     * Attribute value for sort key - RStartT
     */
    private Long startTime;

    /**
     * Attribute value for sort key - RStartT
     */
    private Long endTime;

    /**
     * Get the date of startTime in milliseconds
     * @return date in milliseconds
     */
    public Long getStartDate() {
        return Instant.ofEpochMilli(this.startTime).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
    }
}
