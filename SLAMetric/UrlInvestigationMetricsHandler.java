package com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.lambda.handlers;

import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAONonRetryableException;
import com.amazon.amazonpaydomainvalidationdao.exceptions.AmazonPayDomainValidationDAORetryableException;
import com.amazon.amazonpaydomainvalidationdao.model.UrlInvestigationItem;
import com.amazon.amazonpaymerchanturl.adapter.S3Adapter;
import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.constants.UrlInvestigationMetricEvent;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.processor.UrlInvestigationMetricProcessor;
import com.amazon.amazonpaymerchanturl.urlinvestigationmetrics.utils.MetricUtil;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.MapUtils;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;

/**
 * UrlInvestigationMetricsHandler will query the UrlInvestigation table to fetch a time period of records.
 * It finds the Url review response that breaches SLA and emit respective cloudwatch metrics, generates report in S3
 */
@RequiredArgsConstructor
@Log4j2
public class UrlInvestigationMetricsHandler implements RequestStreamHandler {

    private static final String[] REPORT_HEADINGS = { "Delay Time", "Start Time", "uniqueId", "url" };

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper mapper;
    private final UrlInvestigationMetricProcessor urlInvestigationMetricProcessor;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private final Clock systemClock;
    private final S3Adapter s3Adapter;
    private final String s3BucketNameForReport;

    public UrlInvestigationMetricsHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.mapper = lambdaComponent.providesObjectMapper();
        this.urlInvestigationMetricProcessor = lambdaComponent.provideUrlInvestigationMetricProcessor();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.cloudWatchMetricsHelper = lambdaComponent.providesCloudWatchMetricsHelper();
        this.systemClock = lambdaComponent.provideSystemClock();
        this.s3Adapter = lambdaComponent.providesS3Adapter();
        this.s3BucketNameForReport = lambdaComponent.providesBucketForMetricsReport();
    }

    /**
     * handleRequest entry point for UrlInvestigationMetricsHandler Lambda
     * to initiate monitoring scan for URL review delayed responses which breaches SLA
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context.
     */
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        final String lambdaName = Lambda.lambdaFunctionName(context);
        log.info(lambdaName + " Lambda Invoked");
        UrlInvestigationMetricEvent urlInvestigationMetricEvent;
        try {
            urlInvestigationMetricEvent = mapper.readValue(inputStream, UrlInvestigationMetricEvent.class);
        } catch (Exception e) {
            String errorMsg = "Input parsing failed with exception";
            log.error(errorMsg, e);
            throw new AmazonPayMerchantURLNonRetryableException(errorMsg, e);
        }
        log.info(lambdaName + " is called with urlInvestigationMetricEvent: {}", urlInvestigationMetricEvent);

        try {
            generateMetricReportForInvestigationType(urlInvestigationMetricEvent);
        } catch (Exception e) {
            if (e instanceof AmazonPayDomainValidationDAORetryableException) {
                throw new AmazonPayMerchantURLRetryableException("Dynamo DB retryable exception received ", e);
            } else if (e instanceof AmazonPayDomainValidationDAONonRetryableException) {
                throw new AmazonPayMerchantURLNonRetryableException("Dynamo DB Non-retryable exception received ", e);
            } else {
                String errorMessage = "Exception while executing metric handler.";
                log.error(errorMessage, e);
                throw new AmazonPayMerchantURLNonRetryableException(errorMessage, e);
            }
        }

        sendLambdaResponse(outputStream, lambdaName, SUCCESS_STATUS_CODE, "Success");
    }

    /**
     * Generate Report for the pending investigation Callbacks that has crossed SLA,send metrics in cloudwatch ,
     * generate report and store in S3 bucket
     * @param investigationMetricEvent UrlInvestigationMetricEvent
     */
    private void generateMetricReportForInvestigationType(final UrlInvestigationMetricEvent investigationMetricEvent) {
        //Get the categorized items that have crossed SLA from the urlInvestigationMetricProcessor
        Map<String, List<UrlInvestigationItem>> categorizedItemsForDelayedResponse =
                urlInvestigationMetricProcessor.process(investigationMetricEvent);

        //Send the metrics in cloudwatch for each Metric Category. Send zero count if there are no SLA breaches
        sendCloudWatchMetricsForDelayedResponse(investigationMetricEvent, categorizedItemsForDelayedResponse);

        //create a report in S3 having the details of Urls, review StartTime and SLA breach category etc.
        if (MapUtils.isNotEmpty(categorizedItemsForDelayedResponse)) {
            try {
                publishS3Report(investigationMetricEvent, categorizedItemsForDelayedResponse);
            } catch (IOException e) {
                throw new AmazonPayMerchantURLNonRetryableException(
                        "IO error while creating the csv file for report", e);
            }
        }
    }

    private void publishS3Report(final UrlInvestigationMetricEvent scanEvent,
                                 final Map<String, List<UrlInvestigationItem>> categorizedItems)
            throws IOException {

        final Writer report = new CharArrayWriter();

        final CSVWriter csvWriter = new CSVWriter(report, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.NO_ESCAPE_CHARACTER);

        try {
            csvWriter.writeNext(REPORT_HEADINGS);
            categorizedItems.forEach((delayPeriod, items) ->  writeReportForGivenDelay(csvWriter, delayPeriod, items));

        } catch (Exception e) {
            throw new AmazonPayMerchantURLNonRetryableException("Error while creating the csv file for report", e);
        } finally {
            report.close();
            csvWriter.close();
        }

        final ZonedDateTime currentDateTime = ZonedDateTime.now(systemClock);

        final String s3Key = String.format("%s/%s/%s.csv", currentDateTime.toLocalDate().toString(),
                scanEvent.name(), currentDateTime.toLocalDateTime().toString());

        s3Adapter.putObject(s3BucketNameForReport, s3Key, report.toString(), "text/csv");

        log.info("Successfully published the report in S3");
    }

    private void writeReportForGivenDelay(CSVWriter csvWriter,
                                          String delayPeriod, List<UrlInvestigationItem> items) {

        items.forEach(item -> {
            final Instant reviewStartInstant = Instant.ofEpochMilli(item.getReviewStartTime());

            final String displayableStartTime = ZonedDateTime.ofInstant(reviewStartInstant, systemClock.getZone())
                    .toLocalDateTime().toString();

            final String uniqueId = item.getClientReferenceGroupIdUrl().split("-")[0];
            final String url = item.getClientReferenceGroupIdUrl().split("-")[1];

            final String[] nextLine = {delayPeriod, displayableStartTime, uniqueId, url};

            csvWriter.writeNext(nextLine);
        });
    }

    /**
     * Emit Metrics in Cloud watch based on the delayed SLA categories
     * @param categorizedItems Delayed items categorized
     */
    private void sendCloudWatchMetricsForDelayedResponse(
            UrlInvestigationMetricEvent urlInvestigationMetricEvent,
            Map<String, List<UrlInvestigationItem>> categorizedItems) {

        log.info("Emit Metrics on cloudwatch for {} type", urlInvestigationMetricEvent.getSubInvestigationType());

        //Iterate through all supported metric categories and publish items count in that category
        urlInvestigationMetricEvent.getMetricCategories().entrySet().stream().forEach(
                category -> {
                    cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(String.format("%sMetric",
                                    category.getKey()), MetricUtil.getItemCount(category.getKey(), categorizedItems));

                    log.info("sent metrics for {} category", category.getKey());
                }
        );
    }

    private void sendLambdaResponse(final OutputStream outputStream, final String lambdaName,
                                    final int statusCode, final Object response) {
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(statusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(response)
                .mapper(mapper)
                .build());
    }
}
