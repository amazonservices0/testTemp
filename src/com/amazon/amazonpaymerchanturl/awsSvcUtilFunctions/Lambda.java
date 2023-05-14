package com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions;

import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.sqs.AmazonSQS;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import com.amazonaws.arn.Arn;

import static com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.SQS.sendMsgToLambdaDLQ;
import static com.amazon.amazonpaymerchanturl.constants.MetricConstants.LAMBDA_ASYNC_INVOCATION_FAILED;
import static com.amazon.amazonpaymerchanturl.constants.ModuleConstants.POLLER_SUFFIX;

/**
 * Holds various Util functions for AWS Lambda service.
 */
@Log4j2
public final class Lambda {
    private Lambda() {
    }

    @Builder
    public static class InvokeLambdaAsyncInput {
        private AWSLambda lambdaClient;
        private AmazonSQS sqsClient;
        private String lambdaName, payload;
        private Context context;
        private CloudWatchMetricsHelper cloudWatchMetricsHelper;
    }

    public static boolean invokeLambdaAsync(InvokeLambdaAsyncInput input) {
        final InvokeRequest request = new InvokeRequest().withFunctionName(input.lambdaName)
                .withInvocationType(InvocationType.Event)
                .withPayload(input.payload);
        try {
            input.lambdaClient.invoke(request);
        } catch (Exception e) {
            input.cloudWatchMetricsHelper.publishRecordCountMetricToCloudWatch(LAMBDA_ASYNC_INVOCATION_FAILED);
            log.error("lambda: {} invocation failed. error: {} sending payload back to DLQ. Payload: {}",
                    input.lambdaName, e, input.payload);
            sendMsgToLambdaDLQ(SQS.SendMsgToLambdaDLQInput.builder()
                    .sqsClient(input.sqsClient)
                    .lambdaName(input.lambdaName)
                    .jsonMsg(input.payload)
                    .cloudWatchMetricsHelper(input.cloudWatchMetricsHelper)
                    .context(input.context).build());
            return false;
        }
        return true;
    }

    public static String accountId(Context context) {
        final Arn lambdaARN = Arn.fromString(context.getInvokedFunctionArn());
        return lambdaARN.getAccountId();
    }

    public static String executionRegion(Context context) {
        final Arn lambdaARN = Arn.fromString(context.getInvokedFunctionArn());
        return lambdaARN.getRegion();
    }

    public static String partition(Context context) {
        final Arn lambdaARN = Arn.fromString(context.getInvokedFunctionArn());
        return lambdaARN.getPartition();
    }

    public static String lambdaFunctionName(Context context) {
        final Arn lambdaARN = Arn.fromString(context.getInvokedFunctionArn());
        return lambdaARN.getResource().getResource();
    }

    public static String pollerToOriginFunctionName(Context context) {
        final Arn lambdaARN = Arn.fromString(context.getInvokedFunctionArn());
        final String pollerFunctionName = lambdaARN.getResource().getResource();
        return pollerFunctionName.substring(0, pollerFunctionName.length() - POLLER_SUFFIX.length());
    }
}
