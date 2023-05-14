package com.amazon.meridianservice.adapters;

import com.amazon.meridianservice.exceptions.MeridianServiceNonRetryableException;
import com.amazon.meridianservice.utils.ExceptionHandler;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class S3Adapter {

    private final AmazonS3 amazonS3;
    private final ObjectMapper objectMapper;

    /**
     * Write object as json to s3 uri.
     *
     * @param s3Uri  target s3 uri where json is stored
     * @param object the object to be written to s3
     */
    public void putObject(@NonNull final String s3Uri, @NonNull final Object object) {
        final AmazonS3URI amazonS3URI;
        try {
            amazonS3URI = new AmazonS3URI(s3Uri);
        } catch (RuntimeException e) {
            final String errorMessage = String.format("Invalid S3 file location: %s", s3Uri);
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage, e);
        }

        String stringToWrite;
        try {
            stringToWrite = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            final String errorMessage = String.format("Unable to parse response : %s for s3 bucket",
                    object);
            throw new RuntimeException(errorMessage, e);
        }

        try {
            amazonS3.putObject(amazonS3URI.getBucket(), amazonS3URI.getKey(), stringToWrite);
        } catch (AmazonServiceException e) {
            log.info("Service exception while updating s3 bucket");
            throw new RuntimeException(e);
        } catch (SdkClientException e) {
            log.info("SDK Client failure while updating s3 bucket");
            throw new RuntimeException(e);
        }
    }

    /**
     * Write object to S3 bucket.
     * @param s3BucketName target S3 bucket na,e
     * @param s3Key file name  as S3 key
     * @param content data to be stored in S3
     * @param contentType type of the file like JSON, CSV etc
     */
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

            amazonS3.putObject(putObjectRequest);
        } catch (AmazonS3Exception e) {
            String message = "Failed to put the S3 object which contains the record payload.";
            throw ExceptionHandler.handleAwsSdkServiceException(message, e);
        } catch (AmazonClientException e) {
            String message = "Failed to put the S3 object which contains the record payload.";
            throw new MeridianServiceNonRetryableException(message, e);
        }
    }

}
