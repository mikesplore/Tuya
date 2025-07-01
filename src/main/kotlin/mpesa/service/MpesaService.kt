package com.mike.mpesa.service

import com.google.gson.Gson
import com.mike.database.entities.MpesaTransactionDto
import com.mike.database.repository.MpesaTransactionRepository
import com.mike.database.repository.MeterPaymentRepository
import com.mike.mpesa.config.*
import com.mike.mpesa.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MpesaService(
    private val httpClient: HttpClient,
    private val mpesaConfig: MpesaConfig,
    private val mpesaTransactionRepository: MpesaTransactionRepository,
    private val meterPaymentRepository: MeterPaymentRepository
) {
    private val gson = Gson()
    private var accessToken: String? = null
    private var tokenExpiresAt: LocalDateTime? = null

    // Get access token from M-Pesa API
    private suspend fun getAccessToken(): String {
        // Check if we have a valid token
        if (accessToken != null && tokenExpiresAt != null && LocalDateTime.now().isBefore(tokenExpiresAt)) {
            return accessToken!!
        }

        val credentials = Base64.getEncoder().encodeToString(
            "${mpesaConfig.consumerKey}:${mpesaConfig.consumerSecret}".toByteArray()
        )

        val response: HttpResponse = httpClient.get("${mpesaConfig.baseUrl}/oauth/v1/generate?grant_type=client_credentials") {
            headers {
                append(HttpHeaders.Authorization, "Basic $credentials")
            }
        }

        if (response.status == HttpStatusCode.OK) {
            val tokenResponse = gson.fromJson(response.bodyAsText(), AccessTokenResponse::class.java)
            accessToken = tokenResponse.access_token
            // M-Pesa tokens expire in 3599 seconds, we'll refresh 1 minute early
            tokenExpiresAt = LocalDateTime.now().plusSeconds(3539)
            return accessToken!!
        } else {
            throw Exception("Failed to get access token: ${response.status}")
        }
    }

    // Initiate STK Push payment
    suspend fun initiatePayment(
        amount: BigDecimal,
        phoneNumber: String,
        meterId: UUID,
        userId: UUID,
        accountReference: String = "MeterPayment",
        description: String = "Meter top-up payment"
    ): PaymentResponse {
        try {
            val token = getAccessToken()
            val timestamp = generateTimestamp()
            val password = generateMpesaPassword(mpesaConfig.shortCode, mpesaConfig.passkey, timestamp)
            val formattedPhone = formatPhoneNumber(phoneNumber)

            val stkRequest = StkPushRequest(
                businessShortCode = mpesaConfig.shortCode,
                password = password,
                timestamp = timestamp,
                amount = amount,
                partyA = formattedPhone,
                partyB = mpesaConfig.shortCode,
                phoneNumber = formattedPhone,
                callBackURL = mpesaConfig.callbackUrl,
                accountReference = accountReference,
                transactionDesc = description
            )

            val response: HttpResponse = httpClient.post("${mpesaConfig.baseUrl}/mpesa/stkpush/v1/processrequest") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(gson.toJson(stkRequest))
            }

            val responseBody = response.bodyAsText()
            val stkResponse = gson.fromJson(responseBody, StkPushResponse::class.java)

            // Create M-Pesa transaction record
            val mpesaTransaction = mpesaTransactionRepository.createTransaction(
                amount = amount,
                phoneNumber = formattedPhone,
                merchantRequestId = stkResponse.merchantRequestID,
                checkoutRequestId = stkResponse.checkoutRequestID,
                responseCode = stkResponse.responseCode,
                responseDescription = stkResponse.responseDescription,
                customerMessage = stkResponse.customerMessage
            )

            // Create meter payment record if M-Pesa transaction was created successfully
            val meterPayment = if (stkResponse.responseCode == "0") {
                meterPaymentRepository.createPayment(
                    userId = userId,
                    meterId = meterId,
                    mpesaTransactionId = UUID.fromString(mpesaTransaction.id),
                    amount = amount,
                    description = description
                )
                mpesaTransaction.id
            } else {
                null
            }

            return PaymentResponse(
                success = stkResponse.responseCode == "0",
                message = stkResponse.customerMessage ?: stkResponse.responseDescription ?: "Payment initiated",
                merchantRequestId = stkResponse.merchantRequestID,
                checkoutRequestId = stkResponse.checkoutRequestID,
                mpesaTransactionId = meterPayment
            )

        } catch (e: Exception) {
            return PaymentResponse(
                success = false,
                message = "Payment initiation failed: ${e.message}",
                merchantRequestId = null,
                checkoutRequestId = null,
                mpesaTransactionId = null
            )
        }
    }

    // Process M-Pesa callback
    fun processCallback(callbackData: StkCallbackResponse): Boolean {
        return try {
            val callback = callbackData.body.stkCallback

            // Extract callback metadata
            var mpesaReceiptNumber: String? = null
            var transactionDate: LocalDateTime? = null
            var amount: BigDecimal? = null

            callback.callbackMetadata?.item?.forEach { item ->
                when (item.name) {
                    "MpesaReceiptNumber" -> mpesaReceiptNumber = item.value?.toString()
                    "TransactionDate" -> {
                        item.value?.toString()?.toLongOrNull()?.let { timestamp ->
                            transactionDate = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC)
                        }
                    }
                    "Amount" -> amount = item.value?.toString()?.toBigDecimalOrNull()
                }
            }

            // Update M-Pesa transaction
            val updatedTransaction = mpesaTransactionRepository.updateTransactionFromCallback(
                checkoutRequestId = callback.checkoutRequestID,
                resultCode = callback.resultCode.toString(),
                resultDesc = callback.resultDesc,
                mpesaReceiptNumber = mpesaReceiptNumber,
                transactionDate = transactionDate
            )

            // Update meter payment status if transaction was successful
            if (updatedTransaction != null && callback.resultCode == 0) {
                val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(
                    UUID.fromString(updatedTransaction.id)
                )

                meterPayment?.let {
                    meterPaymentRepository.updatePaymentStatus(
                        paymentId = UUID.fromString(it.id),
                        status = "COMPLETED"
                        // TODO: Add logic to calculate units added and update meter balance
                    )
                }
            } else if (updatedTransaction != null) {
                // Payment failed, update meter payment status
                val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(
                    UUID.fromString(updatedTransaction.id)
                )

                meterPayment?.let {
                    meterPaymentRepository.updatePaymentStatus(
                        paymentId = UUID.fromString(it.id),
                        status = "FAILED"
                    )
                }
            }

            true
        } catch (e: Exception) {
            println("Error processing M-Pesa callback: ${e.message}")
            false
        }
    }

    // Query payment status
    fun queryPaymentStatus(checkoutRequestId: String): MpesaTransactionDto? {
        return mpesaTransactionRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
    }
}
