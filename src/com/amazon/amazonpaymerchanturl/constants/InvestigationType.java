package com.amazon.amazonpaymerchanturl.constants;

import lombok.Getter;

@Getter
public enum InvestigationType {
    /**
     * New Investigation of URL.
     */
    NEW_INVESTIGATION("New_Investigation"),

    /**
     * Periodic investigation of URL.
     */
    PERIODIC_INVESTIGATION("Periodic_Investigation"),

    /**
     * InvestigationType for Merchant Appeal, where the investigator reviews it
     * and takes action on the URL and our system is notified.
     */
    APPEAL("Appeal");

    private String investigationType;

    InvestigationType(final String investigationType) {
        this.investigationType = investigationType;
    }
}
