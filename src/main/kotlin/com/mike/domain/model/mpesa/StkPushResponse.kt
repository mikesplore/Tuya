package com.mike.domain.model.mpesa

import com.google.gson.annotations.SerializedName

data class StkPushResponse(
    @SerializedName("MerchantRequestID") val merchantRequestID: String? = null,
    @SerializedName("CheckoutRequestID") val checkoutRequestID: String? = null,
    @SerializedName("ResponseCode") val responseCode: String? = null,
    @SerializedName("ResponseDescription") val responseDescription: String? = null,
    @SerializedName("CustomerMessage") val customerMessage: String? = null,

    // Error response fields
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
)
