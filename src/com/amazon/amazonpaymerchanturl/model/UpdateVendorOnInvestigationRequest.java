package com.amazon.amazonpaymerchanturl.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * UpdateVendorOnInvestigation model for update vendor investigation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class UpdateVendorOnInvestigationRequest {

    @NonNull
    private String clientReferenceGroupId;

    @NonNull
    private String url;

    @NonNull
    private String subInvestigationType;

    private String investigationId;

    @NonNull
    private String vendorUpdateType;

    @JsonCreator
    public UpdateVendorOnInvestigationRequest(@JsonProperty(value = "clientReferenceGroupId", required = true)
                                                          String clientReferenceGroupId,
                                              @JsonProperty(value = "url", required = true) String url,
                                              @JsonProperty(value = "subInvestigationType", required = true)
                                                          String subInvestigationType,
                                              @JsonProperty(value = "investigationId")
                                                          String investigationId,
                                              @JsonProperty(value = "vendorUpdateType", required = true)
                                                          String vendorUpdateType) {
        this.clientReferenceGroupId = clientReferenceGroupId;
        this.url = url;
        this.subInvestigationType = subInvestigationType;
        this.investigationId = investigationId;
        this.vendorUpdateType = vendorUpdateType;
    }
}
