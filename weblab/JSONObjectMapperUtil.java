package com.amazon.amazonpaymerchanturl.utils;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Utility class to help serialize and deserialize JSON data.
 */
public class JSONObjectMapperUtil {

    private final ObjectMapper mapper;

    public JSONObjectMapperUtil(@NonNull final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Convert POJO to JSON string.
     * @param obj instance of POJO to be serialized into JSON string
     * @return JSON formatted string from input object
     * @throws JsonProcessingException Thrown when JSON string cannot be created from the pojo
     */
    public String serialize(@NonNull final Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            String errMsg = "Failed to serialize given object: " + obj;
            throw new AmazonPayMerchantURLNonRetryableException(errMsg, e);
        }
    }

    /**
     * Create an instance of POJO with classType from JSON string.
     * @param jsonString JSON string to be deserialized into POJO
     * @param classType Type of POJO to be created from input string
     * @return An instance of the POJO created from input string
     * @throws IOException Thrown when input string is not in the correct format
     */
    public <T> T deserialize(@NonNull final String jsonString,
                                    @NonNull final Class<T> classType) {
        try {
            return mapper.readValue(jsonString, classType);
        } catch (JsonProcessingException e) {
            String errMsg = "Failed to deserialize given json String: " + jsonString
                    + " to object of type: " + classType;
            throw new AmazonPayMerchantURLNonRetryableException(errMsg, e);
        }
    }

    /**
     * Create an instance of POJO with classType from Slapshot response JSON string.
     * @param serializedData Slapshot JSON string to be deserialized into POJO
     * @param contractNodeName Name of the output Node in the Slapshot Contract
     * @param classType Type of POJO to be created from input string
     * @return An instance of the POJO created from input string
     * @throws AmazonPayMerchantURLNonRetryableException Thrown when input string is not in the correct format
     */
    public <T> T deserializeSlapshotData(@NonNull final String serializedData,
                                         @NonNull final String contractNodeName,
                                         @NonNull final Class<T> classType) {
        try {
            final JSONObject contractData = new JSONObject(serializedData);
            final JSONObject contractResponseNodeData = contractData.getJSONObject(contractNodeName);
            final String jsonDataString = String.valueOf(contractResponseNodeData.getJSONArray("data"));
            return mapper.readValue(jsonDataString, classType);
        } catch (JSONException | JsonProcessingException e) {
            throw new AmazonPayMerchantURLNonRetryableException(String.format(
                "Failed to deserialize given json String: %s, to object of type: %s ", serializedData, classType), e);
        }
    }
}
