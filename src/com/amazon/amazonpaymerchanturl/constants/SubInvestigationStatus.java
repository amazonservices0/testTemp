package com.amazon.amazonpaymerchanturl.constants;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SubInvestigation status of URL.
 */
@Getter
@RequiredArgsConstructor
public enum SubInvestigationStatus {
    /**
     * If SubInvestigation is in review and yet to be completed.
     */
    IN_REVIEW("In_Review"),

    /**
     * If SubInvestigation is completed and status of URL is compliant.
     */
    COMPLIANT("Compliant"),

    /**
     * If SubInvestigation is completed and status of URL is compliant.
     */
    NON_COMPLIANT("Non_Compliant"),

    /**
     * If SubInvestigation is completed and status of URL is unknown.
     */
    UNKNOWN("Unknown");

    private final String subInvestigationStatus;

    /**
     * Gets sub investigation status from input string value.
     *
     * @param input String
     * @return SubInvestigationStatus
     */
    public static SubInvestigationStatus fromValue(final String input) {
        return Arrays.stream(values())
                .filter(type -> type.subInvestigationStatus.equalsIgnoreCase(input))
                .findFirst().get();
    }
}
