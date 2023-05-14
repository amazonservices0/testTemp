package com.amazon.amazonpaymerchanturl.module;

import javax.inject.Singleton;

import com.amazon.amazonpaymerchanturl.adapter.S3Adapter;
import com.amazon.amazonpaymerchanturl.adapter.SQSAdapter;
import com.amazon.amazonpaymerchanturl.adapter.StepFunctionAdapter;
import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;

import dagger.Module;
import dagger.Provides;

/**
 * Service configuration class for lambda handler.
 * All the lambda specific aws adapters must be defined in AWSAdapterModule.
 */
@Module
public class AWSAdapterModule {

    /**
     * Provides s3 adapter.
     * @param s3Client       the s3 client
     * @return               the s3 adapter
     */
    @Singleton
    @Provides
    public S3Adapter providesS3Adapter(final AmazonS3 s3Client) {
        return new S3Adapter(s3Client);
    }

    /**
     * Provides StepFunction adapter.
     * @param stepFunction   the stepFunction client
     * @return               the stepFunction adapter
     */
    @Singleton
    @Provides
    public StepFunctionAdapter providesStepFunctionAdapter(final AWSStepFunctions stepFunction) {
        return new StepFunctionAdapter(stepFunction);
    }

    /**
     * Provides sqs adapter.
     * @param sqsClient                                     the sqs client
     * @param cloudWatchMetricsHelper                       the cloud watch metrics helper
     * @return                                              sqs adapter
     */
    @Singleton
    @Provides
    public SQSAdapter providesSQSAdapter(final AmazonSQS sqsClient,
                                         final CloudWatchMetricsHelper cloudWatchMetricsHelper) {
        return new SQSAdapter(sqsClient, cloudWatchMetricsHelper);
    }
}
