package com.amazon.amazonpaymerchanturl.model;

import java.io.OutputStream;

import com.amazon.amazonpaymerchanturl.helper.CloudWatchMetricsHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Getter;

/**
 * Pojo for calling a sendResponse from SendResponseUtil.
 */
@Getter
@Builder
public class LambdaResponseInput {
    private OutputStream outputStream;
    private String lambdaName;
    private String details; // TODO: To be deprecated in favor of response attribute
    private Object response;
    private int statusCode;
    private CloudWatchMetricsHelper cloudWatchMetricsHelper;
    private ObjectMapper mapper;
}
