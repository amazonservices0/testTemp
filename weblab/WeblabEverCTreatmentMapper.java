package com.amazon.amazonpaymerchanturl.constants;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Its a class to provide expected weblab treatment for EverC Url Review .
 * Weblab Treatments will be differentiated based on each weblab.
 * Weblab will be differentiated based on UrlSource and UrlType
 * Each Instance will have the webalab Name and respective expected dialed up treatment.
 */
@Getter
@RequiredArgsConstructor
public enum WeblabEverCTreatmentMapper {

    SC_Origin_URL("AP_SELLERCENTRAL_TALUVA_327199", "T2"),
    SC_Redirect_URL("AP_REDIRECT_URL_EVERC_400414", "T1"),
    SC_Store_Front_URL("AP_STORE_FRONT_URL_EVERC_400417", "T1"),

    Swipe_Origin_URL("AP_SWIPE_TALUVA_327916", "T2"),
    Swipe_Redirect_URL("AP_REDIRECT_URL_EVERC_400414", "T1"),
    Swipe_Store_Front_URL("AP_STORE_FRONT_URL_EVERC_400417", "T1"),

    AuthUI_Origin_URL("AP_CHECKOUT_URL_EVERC_400415", "T1"),
    AuthUI_Redirect_URL("AP_CHECKOUT_URL_EVERC_400415", "T1"),
    AuthUI_Store_Front_URL("AP_STORE_FRONT_URL_EVERC_400417", "T1"),

    CheckoutHelper_Origin_URL("AP_CHECKOUT_URL_EVERC_400415", "T1"),
    CheckoutHelper_Redirect_URL("AP_CHECKOUT_URL_EVERC_400415", "T1"),
    CheckoutHelper_Store_Front_URL("AP_STORE_FRONT_URL_EVERC_400417", "T1"),

    NexusService_Origin_URL("AP_CHECKOUT_URL_EVERC_400415", "T1"),
    NexusService_Redirect_URL("AP_CHECKOUT_URL_EVERC_400415", "T1"),
    NexusService_Store_Front_URL("AP_STORE_FRONT_URL_EVERC_400417", "T1");

    private final String weblabName;
    private final String treatment;

    public static WeblabEverCTreatmentMapper getWeblabTreatmentMapper(@NonNull final String urlSource,
                                                                      @NonNull final String urlType) {
        return WeblabEverCTreatmentMapper.valueOf(urlSource + MetricConstants.UNDERSCORE + urlType);
    }
}
