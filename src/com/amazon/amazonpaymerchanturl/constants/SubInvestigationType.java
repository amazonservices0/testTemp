package com.amazon.amazonpaymerchanturl.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubInvestigationType {
    /**
     * Signifies static upfront validation.
     */
    UPFRONT_VALIDATION("Upfront-Validation"),

    /**
     * Signifies manual investigation.
     */
    MANUAL("Manual"),

    /**
     * Signifies automated lightweight investigation.
     */
    AUTO_LIGHTWEIGHT("Auto-LightWeight"),

    /**
     * Signifies automated heavyweight investigation.
     */
    AUTO_HEAVYWEIGHT("Auto-HeavyWeight"),

    /**
     * Signifies automated monitoring investigation.
     */
    AUTO_MONITORING("Auto-Monitoring"),

    /**
     * Signifies operational update in db.
     */
    OPERATIONAL("Operational");

    private final String subInvestigationType;
}
