package com.mike.mpesa.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

// STK Push request model
data class StkPushRequest(
    @SerializedName("BusinessShortCode")
    val businessShortCode: String,
    @SerializedName("Password")
    val password: String,
    @SerializedName("Timestamp")
    val timestamp: String,
    @SerializedName("TransactionType")
    val transactionType: String = "CustomerPayBillOnline",
    @SerializedName("Amount")
    val amount: BigDecimal,
    @SerializedName("PartyA")
    val partyA: String, // Phone number
    @SerializedName("PartyB")
    val partyB: String, // Shortcode
    @SerializedName("PhoneNumber")
    val phoneNumber: String,
    @SerializedName("CallBackURL")
    val callBackURL: String,
    @SerializedName("AccountReference")
    val accountReference: String,
    @SerializedName("TransactionDesc")
    val transactionDesc: String
)

// STK Push response model
data class StkPushResponse(
    @SerializedName("MerchantRequestID")
    val merchantRequestID: String?,
    @SerializedName("CheckoutRequestID")
    val checkoutRequestID: String?,
    @SerializedName("ResponseCode")
    val responseCode: String?,
    @SerializedName("ResponseDescription")
    val responseDescription: String?,
    @SerializedName("CustomerMessage")
    val customerMessage: String?
)

// Access token request
data class AccessTokenRequest(
    val grant_type: String = "client_credentials"
)

// Access token response
data class AccessTokenResponse(
    val access_token: String,
    val expires_in: String
)

// Callback models
data class StkCallback(
    @SerializedName("MerchantRequestID")
    val merchantRequestID: String,
    @SerializedName("CheckoutRequestID")
    val checkoutRequestID: String,
    @SerializedName("ResultCode")
    val resultCode: Int,
    @SerializedName("ResultDesc")
    val resultDesc: String,
    @SerializedName("CallbackMetadata")
    val callbackMetadata: CallbackMetadata? = null // This can be null for failed transactions
)

data class CallbackMetadata(
    @SerializedName("Item")
    val item: List<CallbackItem>
)

data class CallbackItem(
    @SerializedName("Name")
    val name: String,
    @SerializedName("Value")
    val value: Any?
)

data class StkCallbackResponse(
    @SerializedName("Body")
    val body: StkCallbackBody
)

data class StkCallbackBody(
    @SerializedName("stkCallback")
    val stkCallback: StkCallback
)

// Payment request DTOs
data class PaymentRequest(
    val amount: BigDecimal,
    val phoneNumber: String,
    val meterId: String,
    val description: String? = null
)

data class PaymentResponse(
    val success: Boolean,
    val message: String,
    val merchantRequestId: String?,
    val checkoutRequestId: String?,
    val mpesaTransactionId: String?
)
