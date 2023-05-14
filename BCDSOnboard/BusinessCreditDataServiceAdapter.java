package com.amazon.meridianservice.adapters;

import com.amazon.meridianservice.exceptions.MeridianServiceNonRetryableException;
import com.amazon.meridianservice.exceptions.MeridianServiceRetryableException;
import com.amazonaws.services.businesscreditdataservicelambda.BusinessCreditDataServiceLambda;

import com.amazonaws.services.businesscreditdataservicelambda.model.BatchCreditDataConfiguration;
import com.amazonaws.services.businesscreditdataservicelambda.model.BatchInputDataType;
import com.amazonaws.services.businesscreditdataservicelambda.model.BatchInputFileMetadata;
import com.amazonaws.services.businesscreditdataservicelambda.model.BatchInputFileType;
import com.amazonaws.services.businesscreditdataservicelambda.model.BatchOutputFileDestinationMetadata;
import com.amazonaws.services.businesscreditdataservicelambda.model.BatchOutputFormat;
import com.amazonaws.services.businesscreditdataservicelambda.model.BusinessCreditDataServiceLambdaException;
import com.amazonaws.services.businesscreditdataservicelambda.model.ErrorCode;
import com.amazonaws.services.businesscreditdataservicelambda.model.FileStorageSystem;
import com.amazonaws.services.businesscreditdataservicelambda.model.RequestCreditReportBatchRequest;
import com.amazonaws.services.businesscreditdataservicelambda.model.RequestCreditReportBatchResult;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.Set;

/**
 * This Adapter will interact with the BCDS Java Client to fetch data/status of creditReport.
 */
@Log4j2
@SuppressFBWarnings(value = {"EI_EXPOSE_REP2"})
public class BusinessCreditDataServiceAdapter {
    private static final Set<String> BCDS_RETRYABLE_ERROR_CODES = ImmutableSet.of(
            ErrorCode.DATA_PROVIDER_UNAVAILABLE.name(), ErrorCode.DATA_UNAVAILABLE.name());

    private final BusinessCreditDataServiceLambda bcdsClient;
    private final String bcdsResponseS3Bucket;

    @Inject
    public BusinessCreditDataServiceAdapter(
            @NonNull final BusinessCreditDataServiceLambda businessCreditDataServiceLambda,
            @NonNull final String bcdsResponseS3Bucket) {
        this.bcdsClient = businessCreditDataServiceLambda;
        this.bcdsResponseS3Bucket = bcdsResponseS3Bucket;
    }

    /**
     * This method will initiate a batchREquest opf Credit data for Merchant and Marketplace Ids
     * BCDS will return a request Id which is used to fetch the status of the request.
     * POst BCDS batchCreditData processing, the response will be stored in the given output file location
     * @param inputFileS3Path input S3 file path that contains the data of list of merchantIds andMarketPlaceIds
     * @return return BCDS requestId
     */
    public String requestBatchCreditData(final String inputFileS3Path) {

        final RequestCreditReportBatchRequest request = createBatchRequest(inputFileS3Path);
        try {
            final RequestCreditReportBatchResult result = bcdsClient.requestCreditReportBatch(request);
            log.info("requestId for the batch request of input : {} is : {}", inputFileS3Path,
                    result.getRequestId());
            return result.getRequestId();
        } catch (BusinessCreditDataServiceLambdaException e) {
            if (BCDS_RETRYABLE_ERROR_CODES.contains(e.getErrorCode())) {
                throw new MeridianServiceRetryableException("Retryable error from BCDS", e);
            }
            throw new MeridianServiceNonRetryableException("Non-Retryable Exception from BCDS", e);
        } catch (Exception e) {
            throw new MeridianServiceRetryableException(
                    "Unknown Exception while calling BCDS requestCreditReportBatch API", e);
        }

    }

    private RequestCreditReportBatchRequest createBatchRequest(final String inputFilePath) {
        BatchInputFileMetadata inputFileDetail = getBatchInputFileMetaData(inputFilePath);

        BatchOutputFileDestinationMetadata outputFileDestinationDetail = getBatchOutputFileDestinationMetadata();

        BatchCreditDataConfiguration configuration =
                new BatchCreditDataConfiguration().withOutputFormat(BatchOutputFormat.JSON);

        return new RequestCreditReportBatchRequest().withInputFileDetails(inputFileDetail)
                .withOutputDestination(outputFileDestinationDetail)
                .withBatchCreditDataConfiguration(configuration);
    }

    private BatchInputFileMetadata getBatchInputFileMetaData(final String inputFilePath) {
        return new BatchInputFileMetadata().withFileLocation(inputFilePath)
                .withFileStorageSystem(FileStorageSystem.S3)
                .withInputFileType(BatchInputFileType.CSV)
                .withInputDataType(BatchInputDataType.MERCHANT_AND_MARKETPLACE);
    }

    private BatchOutputFileDestinationMetadata getBatchOutputFileDestinationMetadata() {
        return new BatchOutputFileDestinationMetadata().withFileStorageSystem(FileStorageSystem.S3)
                .withOutputDirectory("s3://" + bcdsResponseS3Bucket);
    }
}
