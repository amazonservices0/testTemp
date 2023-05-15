package com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.utils;

import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.constants.SubInvestigationType;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.constants.UrlInvestigationMetricEvent;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.model.TimeRange;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ZERO;

/**
 * Utility class for Investigation Metrics
 */
@Log4j2
@UtilityClass
public class MetricUtil {

    /**
     * returns the counts of items existing with a key in the map
     * @param map Map with keys and list of UrlInvestigationItem as Value
     * @param key Key of a Map
     * @return count of the values associated with the Key . Return ZERO, if the Key doesn't exist
     */
    public int getItemCount(@NonNull final String key, final Map<String, List<UrlInvestigationItem>> map) {
        if (!map.containsKey(key)) {
            return INTEGER_ZERO;
        }
        return map.get(key).size();
    }

    /**
     * This method will provide the list of {@link TimeRange} between startTime and EndTime splitted by day wise
     * @param startTime startTime in millisec
     * @param endTime endTime in millisec
     * @return the list of TimeRange split by each day
     */
    public List<TimeRange> getQueryListForTimeRange(@NonNull final Long startTime,
                                                           @NonNull final Long endTime) {
        List<TimeRange> queryTimeRangesByDay = new ArrayList<>();
        Instant startTimeInstant = Instant.ofEpochMilli(startTime);
        Instant endTimeInstant = Instant.ofEpochMilli(endTime);

        Instant startDayInstant = startTimeInstant.truncatedTo(DAYS);
        Instant endDayInstant = endTimeInstant.truncatedTo(DAYS);

        Instant nextDayInstant;
        //while start and end time is on different days, split them by day wise
        while (startDayInstant.isBefore(endDayInstant)) {
            nextDayInstant = startDayInstant.plus(1, DAYS);

            queryTimeRangesByDay.add(new TimeRange(startTimeInstant.toEpochMilli(),
                    nextDayInstant.minusMillis(1).toEpochMilli()));

            startTimeInstant = nextDayInstant;
            startDayInstant = nextDayInstant;
        }

        //start and end time is on same day
        queryTimeRangesByDay.add(new TimeRange(startTimeInstant.toEpochMilli(),
                endTimeInstant.toEpochMilli()));
        return queryTimeRangesByDay;
    }

    /**
     * Returns the duration between two timestamps in milliseconds considering business days instead of calendar days
     * @param startTimeStamp start time in milliseconds
     * @param endTimeStamp end time in milliseconds
     * @param event UrlInvestigationEvent
     * @return duration in milliseconds
     */
    public Long getBusinessDaysDuration(@NonNull final Long startTimeStamp,
                                               @NonNull final Long endTimeStamp,
                                               @NonNull final UrlInvestigationMetricEvent event) {
        Long durationBetweenTimes = 0L;

        LocalDateTime startTime = Instant.ofEpochMilli(startTimeStamp).atZone(UTC).toLocalDateTime();
        LocalDateTime endTime = Instant.ofEpochMilli(endTimeStamp).atZone(UTC).toLocalDateTime();

        LocalDateTime startDay = startTime.truncatedTo(DAYS);
        LocalDateTime endDay = endTime.truncatedTo(DAYS);

        LocalDateTime nextDay;
        //while start and end time is on different days, split them by day wise
        while (startDay.isBefore(endDay)) {
            nextDay = startDay.plusDays(1);
            if (isBusinessDayForInvestigationType(startTime, event)) {
                durationBetweenTimes += Duration.between(startTime, nextDay).toMillis();
            }
            startTime = nextDay;
            startDay = nextDay;
        }

        //start and end time is on same day
        if (isBusinessDayForInvestigationType(startTime, event)) {
            durationBetweenTimes += Duration.between(startTime, endTime).toMillis();
        }
        log.info("Duration between StartTime : {} and EndTime : {}, is {} : ",
                Instant.ofEpochMilli(startTimeStamp), Instant.ofEpochMilli(endTimeStamp),
                Duration.ofMillis(durationBetweenTimes));
        return durationBetweenTimes;
    }

    /**
     * Checks whether the day of timestamp is a business day or not for the investigation type.
     * As the SLA for LightWeight Scan is 1hr. All days are considered as business days for Lightweight scan
     * @param dateTime timestamp of a day
     * @param event InvestigationType
     * @return true for business days , false for Non-business days
     */
    private boolean isBusinessDayForInvestigationType(final LocalDateTime dateTime,
                                                      final UrlInvestigationMetricEvent event) {
        if (event.equals(UrlInvestigationMetricEvent.AUTO_LIGHTWEIGHT)
                || event.getSubInvestigationType().equals(SubInvestigationType.MANUAL.getSubInvestigationType())) {
            return true;
        } else {
            return (dateTime.getDayOfWeek() != SATURDAY && dateTime.getDayOfWeek() != SUNDAY);
        }
    }
}
