package com.amazon.amazonpaymerchanturl.constants;

import org.apache.http.HttpStatus;

/**
 * Constants for the SendResponseUtil.
 */
public final class ResponseConstants {
    private ResponseConstants(){
    }

    /**
     *  Defined a static variable for the statusCode string.
     */
    public static final String STATUS_CODE_NAME = "statusCode";

    /**
     * Static variable that store the success code i.e. 200.
     */
    public static final int SUCCESS_STATUS_CODE = HttpStatus.SC_OK;

    /**
     * Static variable that store the Failed code i.e. 500.
     */
    public static final int FAILED_STATUS_CODE = HttpStatus.SC_INTERNAL_SERVER_ERROR;

    /**
     * Static variable that store the Failed code i.e. 400.
     */
    public static final int BAD_REQUEST_FAILED_STATUS_CODE = HttpStatus.SC_BAD_REQUEST;

    /**
     * Static variable that store the Failed code i.e. 404.
     */
    public static final int NOT_FOUND_FAILED_STATUS_CODE = HttpStatus.SC_NOT_FOUND;

    /**
     * Defined a static variable for the body string.
     */
    public static final String BODY_NAME = "body";

    /**
     * Defined a static variable for the Bad_REQUEST msg.
     */
    public static final String BAD_REQUEST = "Bad Request";

    /**
     * Defined a static variable for the NOT_FOUND msg.
     */
    public static final String NOT_FOUND = "No Data found for the given request";

    /**
     * Defined a static variable for the INTERNAL_SERVER_ERROR msg.
     */
    public static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
}

