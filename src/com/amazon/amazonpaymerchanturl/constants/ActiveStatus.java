package com.amazon.amazonpaymerchanturl.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * Contains values for a update type.
 */
public enum ActiveStatus {

    /**
     * If url is in active state.
     */
    ACTIVE("Active"),

    /**
     * If url is not active
     */
    DISABLED("Disabled");

    private final String value;
}
