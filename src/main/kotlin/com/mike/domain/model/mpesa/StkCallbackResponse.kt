package com.mike.domain.model.mpesa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StkCallbackResponse(
    @SerialName("Body") val body: StkCallbackBody
)

@Serializable
data class StkCallbackBody(
    @SerialName("stkCallback") val stkCallback: StkCallback
)

@Serializable
data class StkCallback(
    @SerialName("MerchantRequestID") val merchantRequestID: String? = null,
    @SerialName("CheckoutRequestID") val checkoutRequestID: String? = null,
    @SerialName("ResultCode") val resultCode: Int? = null,
    @SerialName("ResultDesc") val resultDesc: String? = null,
    @SerialName("CallbackMetadata") val callbackMetadata: CallbackMetadata? = null
)

@Serializable
data class CallbackMetadata(
    @SerialName("Item") val item: List<CallbackItem>? = null
)

@Serializable
data class CallbackItem(
    @SerialName("Name") val name: String,
    @SerialName("Value") val value: kotlinx.serialization.json.JsonElement? = null
)

