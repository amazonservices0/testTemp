package com.amazon.amazonpaymerchanturl.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode
public class DeleteUrlNotificationMessage extends UrlNotificationMessage {
    private final String urlStatus;
}
