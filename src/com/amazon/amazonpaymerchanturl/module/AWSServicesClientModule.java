package com.amazon.amazonpaymerchanturl.module;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public class AWSServicesClientModule {

    /**
     * Provides aws lambda client - AWSLambda.
     * @return AWSLambda
     */
    @Singleton
    @Provides
    public AWSLambda providesLambdaServiceClient() {
        return AWSLambdaClientBuilder.defaultClient();
    }

    /**
     * Provides aws step functions client - AWSStepFunctions.
     *
     * @return AWSStepFunctions
     */
    @Singleton
    @Provides
    public AWSStepFunctions providesStepFunctionClient() {
        return AWSStepFunctionsClientBuilder.defaultClient();
    }

    /**
     * Provides amazon sqs client - AmazonSQS.
     * @return AmazonSQS
     */
    @Singleton
    @Provides
    public AmazonSQS providesSQSServiceClient() {
        return AmazonSQSClientBuilder.defaultClient();
    }

    /**
     * Provides amazon s3 client - AmazonS3.
     * @return AmazonS3
     */
    @Singleton
    @Provides
    public AmazonS3 providesS3ServiceClient() {
        return AmazonS3ClientBuilder.defaultClient();
    }
}
