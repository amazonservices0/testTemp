package com.amazon.amazonpaymerchanturl.model;

import com.amazon.identity.stego.Cf3pApplication;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Request parameter to call nCfApplicationUpdate API
 * of Stego Service.
 */
@Builder
@Value
public class StegoServiceUpdateRequest {
    @NonNull
    private String appId;

    @NonNull
    private String appHash;

    private Cf3pApplication applicationData;
}
