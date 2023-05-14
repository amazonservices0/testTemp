package com.amazon.amazonpaymerchanturl.model.postUrlReviewAction;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PostUrlReviewActionResponse {

    private List<PostUrlReviewActionResponseItem> successResponseItemList;

    private List<PostUrlReviewActionResponseItem> failedResponseItemList;

    public PostUrlReviewActionResponse() {
        successResponseItemList = new ArrayList<>();
        failedResponseItemList = new ArrayList<>();
    }
}
