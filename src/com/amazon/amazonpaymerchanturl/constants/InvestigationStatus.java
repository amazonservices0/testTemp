package com.amazon.amazonpaymerchanturl.constants;

import lombok.Getter;

import java.util.Arrays;

/**
 * Investigation status of URL. If any change is made here please make sure
 * API Gateway validation is also updated.
 */
@Getter
public enum InvestigationStatus {
    /**
     * If investigation is in Review and yet to be completed.
     */
    IN_REVIEW("In_Review"),
    /**
     * If investigation is completed and status of URL is compliant.
     */
    COMPLIANT("Compliant"),
    /**
     * If investigation is completed and status of URL is compliant.
     */
    NON_COMPLIANT("Non_Compliant"),
    /**
     * If investigation is periodic and status of the URL was compliant, then Compliant_To_In_Review.
     */
    COMPLIANT_TO_IN_REVIEW("Compliant_To_In_Review"),
    /**
     * If investigation is periodic and status of the URL was non compliant, then Non_Compliant_To_In_Review.
     */
    NON_COMPLIANT_TO_IN_REVIEW("Non_Compliant_To_In_Review"),

    /**
     * If investigation could not complete due to some error and status of URL is not yet known.
     */
    UNKNOWN("Unknown");

    private final String investigationStatus;
    InvestigationStatus(final String investigationStatus) {
        this.investigationStatus = investigationStatus;
    }

    public static InvestigationStatus fromValue(final String input) {
        return Arrays.stream(values())
                .filter(currStatus -> currStatus.investigationStatus.equalsIgnoreCase(input))
                .findFirst().orElse(InvestigationStatus.UNKNOWN);
    }
}
