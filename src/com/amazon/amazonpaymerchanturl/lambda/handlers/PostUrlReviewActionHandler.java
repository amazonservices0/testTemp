package com.amazon.amazonpaymerchanturl.lambda.handlers;

import com.amazon.amazonpaymerchanturl.awsSvcUtilFunctions.Lambda;
import com.amazon.amazonpaymerchanturl.component.DaggerLambdaComponent;
import com.amazon.amazonpaymerchanturl.component.LambdaComponent;
import com.amazon.amazonpaymerchanturl.factory.PostUrlReviewActionTaskFactory;
import com.amazon.amazonpaymerchanturl.model.LambdaResponseInput;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionRequest;
import com.amazon.amazonpaymerchanturl.model.postUrlReviewAction.PostUrlReviewActionResponse;
import com.amazon.amazonpaymerchanturl.utils.LambdaResponseUtil;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.io.OutputStream;

import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.FAILED_STATUS_CODE;
import static com.amazon.amazonpaymerchanturl.constants.ResponseConstants.SUCCESS_STATUS_CODE;

/**
 * PostUrlReviewActionHandler perform actions after the url review completion.
 */
@RequiredArgsConstructor
@Log4j2
public class PostUrlReviewActionHandler implements RequestStreamHandler {

    private final LambdaComponent lambdaComponent;
    private final ObjectMapper objectMapper;
    private final LambdaResponseUtil lambdaResponseUtil;
    private final PostUrlReviewActionTaskFactory postUrlReviewActionTaskFactory;

    public PostUrlReviewActionHandler() {
        lambdaComponent = DaggerLambdaComponent.create();
        lambdaComponent.initializeAppConfig();
        this.objectMapper = lambdaComponent.providesObjectMapper();
        this.lambdaResponseUtil = lambdaComponent.provideLambdaResponseUtil();
        this.postUrlReviewActionTaskFactory = lambdaComponent.providesPostUrlReviewActionTaskFactory();
    }

    /**
     * handleRequest entry point for PostUrlReviewAction Lambda to operational use.
     *
     * @param inputStream  input for the lambda.
     * @param outputStream output after handler.
     * @param context      context
     */
    public void handleRequest(final InputStream inputStream, final OutputStream outputStream,
                              final Context context) {
        final String lambdaName = Lambda.lambdaFunctionName(context);
        log.info(lambdaName + " Lambda invoked");

        PostUrlReviewActionRequest postUrlReviewActionRequest;
        try {
            postUrlReviewActionRequest = objectMapper.readValue(inputStream, PostUrlReviewActionRequest.class);
        } catch (Exception e) {
            log.error("Exception: {} is reported in lambda: {}", e, lambdaName);
            sendLambdaResponse(outputStream, lambdaName, FAILED_STATUS_CODE, "input parsing failed.");
            return;
        }

        log.info(lambdaName + " is called with input: {}", postUrlReviewActionRequest);

       PostUrlReviewActionResponse postUrlReviewActionResponse;
        try {
            postUrlReviewActionResponse = postUrlReviewActionTaskFactory
                    .produceTaskHandler(postUrlReviewActionRequest.getTaskType())
                    .handleTask(postUrlReviewActionRequest);
            sendLambdaResponse(outputStream, lambdaName, SUCCESS_STATUS_CODE, postUrlReviewActionResponse);
        } catch (Exception e) {
            log.info("Invalid request: {}", postUrlReviewActionRequest, e);
            sendLambdaResponse(outputStream, lambdaName, FAILED_STATUS_CODE, "Invalid request");
        }
    }

    private void sendLambdaResponse(final OutputStream outputStream, final String lambdaName,
                                    final int statusCode, final Object response) {
        lambdaResponseUtil.sendResponseJson(LambdaResponseInput.builder()
                .statusCode(statusCode)
                .outputStream(outputStream)
                .lambdaName(lambdaName)
                .response(response)
                .mapper(objectMapper)
                .build());
    }
}
