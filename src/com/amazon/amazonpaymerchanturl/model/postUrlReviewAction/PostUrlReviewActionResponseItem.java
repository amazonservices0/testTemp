package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostUrlReviewActionResponseItem {

    private String clientReferenceGroupId;

    private String url;

    private String investigationId;
}
