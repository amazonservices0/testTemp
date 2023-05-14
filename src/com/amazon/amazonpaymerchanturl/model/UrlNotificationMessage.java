package com.amazon.amazonpaymerchanturl.model;

import com.amazon.amazonpaymerchanturl.constants.UpdateStatusType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode
public abstract class UrlNotificationMessage {
    private final String clientReferenceGroupId;
    private final String url;
    private final UpdateStatusType updateStatusType;
}
