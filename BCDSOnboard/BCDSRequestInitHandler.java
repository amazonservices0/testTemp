package com.amazon.meridianservice.handler;


import com.amazon.meridianservice.adapters.BusinessCreditDataServiceAdapter;
import com.amazon.meridianservice.adapters.S3Adapter;
import com.amazon.meridianservice.exceptions.MeridianServiceNonRetryableException;
import com.amazon.meridianservice.model.lambda.BCDSOnboardInitRequest;
import com.amazon.meridianservice.model.lambda.BCDSOnboardInitResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.amazon.meridianservice.constants.ModuleConstants.BCDS_REQUEST_S3_BUCKET;

@Log4j2
@SuppressFBWarnings(value = {"EI_EXPOSE_REP2"})
public final class BCDSRequestInitHandler extends LambdaHandler<BCDSOnboardInitRequest,
        BCDSOnboardInitResponse> {
    private static final String[] REPORT_HEADINGS = {"merchant_id", "marketplace_id", "credit_bureau"};

    private final ObjectMapper objectMapper;
    private final S3Adapter s3Adapter;
    private final BusinessCreditDataServiceAdapter businessCreditDataServiceAdapter;
    private final String bcdsS3Bucket;

    @Inject
    public BCDSRequestInitHandler(@NonNull final ObjectMapper objectMapper,
                                  @NonNull final S3Adapter s3Adapter,
                                  @NonNull final BusinessCreditDataServiceAdapter businessCreditDataServiceAdapter,
                                  @Named(BCDS_REQUEST_S3_BUCKET) final String bcdsS3Bucket) {
        super(objectMapper, BCDSOnboardInitRequest.class, BCDSOnboardInitResponse.class);

        this.objectMapper = objectMapper;
        this.s3Adapter = s3Adapter;
        this.businessCreditDataServiceAdapter = businessCreditDataServiceAdapter;
        this.bcdsS3Bucket = bcdsS3Bucket;
    }

    @Override
    public BCDSOnboardInitResponse handleRequest(BCDSOnboardInitRequest onboardingRequest, Context context) {
        log.info(context.getFunctionName() + " lambda Called");

        final String s3Key;
        try {
            s3Key = publishS3MerchantIdInputData(onboardingRequest.getMarketplaceIdMerchantIdsMap());
        } catch (IOException e) {
            throw new MeridianServiceNonRetryableException(
                    "IO error while creating the csv file for BCDS batch credit request", e);
        }

        String requestId = businessCreditDataServiceAdapter.requestBatchCreditData(
                "s3://" + bcdsS3Bucket + "/" + s3Key);
        return BCDSOnboardInitResponse.builder().requestId(requestId).build();
    }

    private String publishS3MerchantIdInputData(final Map<String, List<String>> marketplaceIdMerchantIdsMap)
            throws IOException {

        final Writer batchInput = new CharArrayWriter();
        final CSVWriter csvWriter = new CSVWriter(batchInput, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.NO_ESCAPE_CHARACTER);

        try {
            csvWriter.writeNext(REPORT_HEADINGS);
            marketplaceIdMerchantIdsMap.forEach((marketplaceId, merchantIds) ->
                    writeBatchFileForMerchantIdMarketPlaceId(csvWriter, marketplaceId, merchantIds));
        } catch (Exception e) {
            throw new MeridianServiceNonRetryableException(
                    "Error while creating the csv file for BCDS batch credit request", e);
        } finally {
            batchInput.close();
            csvWriter.close();
        }

        final ZonedDateTime currentDateTime = ZonedDateTime.now();
        final String s3Key = String.format("%s.csv", currentDateTime.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")));
        s3Adapter.putObject(bcdsS3Bucket, s3Key, batchInput.toString(), "text/csv");

        log.info("Successfully published the MerchantIds and MarketplaceIds in S3");

        return s3Key;
    }

    private void writeBatchFileForMerchantIdMarketPlaceId(CSVWriter csvWriter,
                                                          String marketplaceId, List<String> merchantIds) {
        merchantIds.forEach(merchantId -> {
            final String[] nextLine = {merchantId, marketplaceId, ""};
            csvWriter.writeNext(nextLine);
        });
    }
}
