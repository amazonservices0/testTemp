package com.amazon.amazonpaymerchanturl.constants;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLInvalidInputException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum VendorUpdateType {
    /**
     * If type is deboard url then deboard the url from the vendor.
     */
    DEBOARD_URL("DeboardUrl"),

    /**
     * If type is onboard url then onboard the url from the vendor investigation.
     */
    ONBOARD_URL("OnboardUrl"),

    UNKNOWN("Unknown");

    private final String vendorUpdateType;

    /**
     * Gets update vendor investigation type from string value.
     *
     * @param value String
     * @return postUrlReviewActionTaskType
     */
    public static VendorUpdateType fromValue(final String value) {
        return Arrays.stream(values())
                .filter(type -> type.vendorUpdateType.equalsIgnoreCase(value))
                .findFirst().orElseThrow(() ->
                        new AmazonPayMerchantURLInvalidInputException("Invalid vendor update value: " + value));
    }

}
