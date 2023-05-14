package com.amazon.amazonpaymerchanturl.constants;

/**
 * Constants for vendor review.
 */
public final class VendorReviewConstants {

    private VendorReviewConstants() {}

    /**
     * EverCompliant id.
     *
     */
    public static final String EVERCOMPLIANT_ID = "A1AHPADGJGV48";

    /**
     * Vendor id string
     */
    public static final String VENDOR_ID = "VendorId";

    /**
     * Extended paylaod size message attribute.
     *
     */
    public static final String EXTENDED_PAYLOAD_SIZE = "ExtendedPayloadSize";

    /**
     * SQS large payload size message attribute.
     */
    public static final String SQS_LARGE_PAYLOAD_SIZE = "SQSLargePayloadSize";

    /**
     * External MessageS3Pointer class to be replaced.
     */
    public static final String EXTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS
            = "com.amazon.sqs.javamessaging.MessageS3Pointer";

    /**
     * External PayloadS3Pointer class to be replaced.
     */
    public static final String EXTERNAL_PAYLOAD_S3_POINTER_MODEL_CLASS
            = "software.amazon.payloadoffloading.PayloadS3Pointer";

    /**
     * Internal MessageS3Pointer class used for deserialization.
     */
    public static final String INTERNAL_MESSAGE_S3_POINTER_MODEL_CLASS
            = "com.amazon.amazonpaymerchanturl.model.MessageS3Pointer";

    /**
     * Initiate Vendor Review Success Message.
     */
    public static final String INITIATE_VENDOR_REVIEW_SUCCESS_MESSAGE = "Successfully initiated vendor review";

    /**
     * Initiate Vendor Review Failure Message.
     */
    public static final String INITIATE_VENDOR_REVIEW_FAILURE_MESSAGE = "Failed to initiate vendor review.";

    /**
     * Execute Manual Response Workflow Success Message.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_SUCCESS_MESSAGE
            = "Successfully started/resumed the workflow";

    /**
     * Execute Manual Response Workflow Failure Message.
     */
    public static final String EXECUTE_MANUAL_RESPONSE_WORKFLOW_FAILURE_MESSAGE
            = "Failed to start/resume the workflow";

    /**
     * Update Vendor on Investigation Success Message.
     */
    public static final String UPDATE_VENDOR_ON_INVESTIGATION_SUCCESS_MESSAGE
            = "Successfully De-boarded/Re-onboarded the url";

    /**
     * Delimiter for initiate Vendor Review.
     */
    public static final String DELIMITER = "_";
}
