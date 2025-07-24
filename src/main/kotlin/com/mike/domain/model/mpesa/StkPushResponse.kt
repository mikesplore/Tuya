package com.mike.domain.model.mpesa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StkPushResponse(
    @SerialName("MerchantRequestID") val merchantRequestID: String? = null,
    @SerialName("CheckoutRequestID") val checkoutRequestID: String? = null,
    @SerialName("ResponseCode") val responseCode: String? = null,
    @SerialName("ResponseDescription") val responseDescription: String? = null,
    @SerialName("CustomerMessage") val customerMessage: String? = null,

    // Error response fields
    @SerialName("requestId") val requestId: String? = null,
    @SerialName("errorCode") val errorCode: String? = null,
    @SerialName("errorMessage") val errorMessage: String? = null
)
