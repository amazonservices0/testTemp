package com.amazon.amazonpaymerchanturl.model;

import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class ManualUrlReview {
    private List<String> urlList;
}
