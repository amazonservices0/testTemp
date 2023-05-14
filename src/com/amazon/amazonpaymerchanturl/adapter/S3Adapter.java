package com.amazon.amazonpaymerchanturl.adapter;

import javax.inject.Singleton;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import static com.amazon.amazonpaymerchanturl.utils.ExceptionHandlers.handleAwsSdkServiceException;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * S3 adapter for fetching records.
 */
@Log4j2
@Singleton
public class S3Adapter {

    private final AmazonS3 s3Client;

    public S3Adapter(final AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Gets records using S3 key and bucket.
     * @param s3BucketName the bucket name
     * @param s3Key the s3 key
     * @return s3RecordObject
     */
    public String getRecords(@NonNull final String s3BucketName, @NonNull final String s3Key) {
        try {
            log.info("Calling S3 client to get record object of s3 key : " + s3Key
                    + "from bucket : " + s3BucketName);
            return s3Client.getObjectAsString(s3BucketName, s3Key);
        } catch (AmazonS3Exception e) {
            String message = "Failed to get the S3 object which contains the record payload.";
            throw handleAwsSdkServiceException(message, e);
        } catch (AmazonClientException e) {
            String message = "Failed to get the S3 object which contains the record payload.";
            throw new AmazonPayMerchantURLNonRetryableException(message, e);
        }
    }

    public void putObject(@NonNull final String s3BucketName, @NonNull final String s3Key,
                          @NonNull final String content, final String contentType) {

        final byte[] contentByteArray = content.getBytes(StandardCharsets.UTF_8);

        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentByteArray.length);

        final PutObjectRequest putObjectRequest = new PutObjectRequest(s3BucketName, s3Key,
                                              new ByteArrayInputStream(contentByteArray), metadata);

        try {

            log.info("Calling S3 client to put object of length " + content.length() + " at s3 key : " + s3Key
                    + "from bucket : " + s3BucketName);

            s3Client.putObject(putObjectRequest);
        } catch (AmazonS3Exception e) {
            String message = "Failed to put the S3 object which contains the record payload.";
            throw handleAwsSdkServiceException(message, e);
        } catch (AmazonClientException e) {
            String message = "Failed to put the S3 object which contains the record payload.";
            throw new AmazonPayMerchantURLNonRetryableException(message, e);
        }
    }
}
