package com.amazon.amazonpaymerchanturl.helper;

import com.amazon.amazonpaymerchanturl.provider.WeblabTreatmentInformationProvider;
import com.amazon.amazonpaymerchanturl.utils.CustomInfoUtil;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

import static com.amazon.amazonpaymerchanturl.constants.HandlerConstants.TREATMENT_T1;

@Log4j2
public class WeblabHelper {

    private static final String AP_DEDUPE_VARIANT_URLS_WEBLAB = "AP_DEDUPE_VARIANT_URLS_DVS_534172";
    private final WeblabTreatmentInformationProvider weblabProvider;

    @Inject
    public WeblabHelper(@NonNull WeblabTreatmentInformationProvider weblabProvider) {
        this.weblabProvider = weblabProvider;
    }

    public boolean isWeblabDialedUpForDedupingVariantUrls(@NonNull String clientReferenceGrouppId) {
        final String merchantId = CustomInfoUtil.getMerchantId(clientReferenceGrouppId);
        final String marketPlaceID = CustomInfoUtil.getMarketplaceId(clientReferenceGrouppId);
        final String treatment = weblabProvider.getWebLabTreatmentInformation(merchantId,
                marketPlaceID, AP_DEDUPE_VARIANT_URLS_WEBLAB);
        return TREATMENT_T1.equals(treatment);
    }

    public String getWeblabTreatment(@NonNull final String weblabName, @NonNull final String merchantId,
                                     @NonNull final String marketplaceId) {
        return weblabProvider.getWebLabTreatmentInformation(merchantId, marketplaceId, weblabName);
    }
}
