package com.amazon.amazonpaymerchanturl.helper;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLCloudwatchMetricException;
import com.amazon.lambdaskurge.adapter.MetricsAdapter;
import com.amazonaws.services.cloudwatch.model.InternalServiceException;
import com.amazonaws.services.cloudwatch.model.InvalidParameterCombinationException;
import com.amazonaws.services.cloudwatch.model.InvalidParameterValueException;
import com.amazonaws.services.cloudwatch.model.MissingRequiredParameterException;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import javax.inject.Inject;

/***
 * This class is responsible for publishing custom metrics to cloudwatch.
 */
@Log4j2
public class CloudWatchMetricsHelper {

    private final MetricsAdapter metricsAdapter;

    @Inject
    public CloudWatchMetricsHelper(@NonNull MetricsAdapter metricsAdapter) {
        this.metricsAdapter = metricsAdapter;
    }

    /***
     * This method internally invoked recordCount() method for publishing custom metrics to cloudwatch.
     * @param metricName - the metric name
     */
    public void publishRecordCountMetricToCloudWatch(@NonNull final String metricName) {
        try {
            metricsAdapter.recordCount(metricName);
            log.debug("{} Published Successfully.", metricName);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /***
     * This method internally invoked recordCount() method with value for publishing custom metrics to cloudwatch.
     * @param metricName - the metric name
     */
    public void publishRecordCountMetricToCloudWatch(@NonNull final String metricName, final int value) {
        try {
            metricsAdapter.recordCount(metricName, value);
            log.debug("{} Published Successfully.", metricName);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * This method holds the logic of exception handling
     *
     * @param e exception name
     */
    private void handleException(Exception e) {
        String message = "Unable to publish metric to cloudwatch, ";

        if (e instanceof InvalidParameterValueException) {
            message += "The value of an input parameter is bad or out-of-range.";
        } else if (e instanceof MissingRequiredParameterException) {
            message += "An input parameter that is required is missing.";
        } else if (e instanceof InvalidParameterCombinationException) {
            message += "Parameters were used together that cannot be used together.";
        } else if (e instanceof InternalServiceException) {
            message += "Request processing has failed due to some unknown error, exception, or failure.";
        }

        throw new AmazonPayMerchantURLCloudwatchMetricException(message, e);
    }
}
