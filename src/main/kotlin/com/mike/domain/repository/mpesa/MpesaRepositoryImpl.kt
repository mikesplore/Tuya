package com.mike.domain.repository.mpesa

import com.google.gson.Gson
import com.mike.database.tables.MpesaTransactions
import com.mike.domain.model.mpesa.*
import com.mike.domain.repository.meter.MeterPaymentRepository
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MpesaRepositoryImpl(
    private val httpClient: HttpClient,
    private val meterPaymentRepository: MeterPaymentRepository
) : MpesaRepository {
    private val gson = Gson()
    private var accessToken: String? = null
    private var tokenExpiresAt: LocalDateTime? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    data class MpesaConfig(
        val consumerKey: String,
        val consumerSecret: String,
        val shortCode: String,
        val passkey: String = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919", // Sandbox passkey
        val baseUrl: String = "https://sandbox.safaricom.co.ke", // Sandbox URL
        val callbackUrl: String = "https://f2fa-197-237-48-114.ngrok-free.app/mpesa/callback" // Default callback URL
    )

    override fun getMpesaConfig(): MpesaConfig {
        val dotenv = dotenv {
            directory = "."
            ignoreIfMissing = true
        }

        return MpesaConfig(
            consumerKey = dotenv["CONSUMER_KEY"] ?: throw IllegalStateException("CONSUMER_KEY not found in .env"),
            consumerSecret = dotenv["CONSUMER_SECRET"]
                ?: throw IllegalStateException("CONSUMER_SECRET not found in .env"),
            shortCode = dotenv["SHORT_CODE"] ?: throw IllegalStateException("SHORT_CODE not found in .env"),
            callbackUrl = dotenv["MPESA_CALLBACK_URL"] ?: "https://7b9b-41-89-128-6.ngrok-free.app/mpesa/callback"
        )
    }

    override suspend fun getAccessToken(): String {
        if (accessToken != null && tokenExpiresAt != null && LocalDateTime.now().isBefore(tokenExpiresAt)) {
            return accessToken!!
        }

        val mpesaConfig = getMpesaConfig()
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
            tokenExpiresAt = LocalDateTime.now().plusSeconds(3539)
            return accessToken!!
        } else {
            throw Exception("Failed to get access token: ${response.status}")
        }
    }

    override suspend fun initiateStk(
        amount: BigDecimal,
        phoneNumber: String,
        accountReference: String,
        description: String
    ): StkPushResponse {
        val mpesaConfig = getMpesaConfig()
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
            timeout {
                requestTimeoutMillis = 30000
            }
        }

        val responseBody = response.bodyAsText()
        println("M-Pesa STK push response: $responseBody")
        return gson.fromJson(responseBody, StkPushResponse::class.java)
    }

    override fun generateMpesaPassword(shortCode: String, passkey: String, timestamp: String): String {
        val data = "$shortCode$passkey$timestamp"
        return Base64.getEncoder().encodeToString(data.toByteArray())
    }

    override fun generateTimestamp(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        return LocalDateTime.now().format(formatter)
    }

    override fun formatPhoneNumber(phoneNumber: String): String {
        val digits = phoneNumber.replace(Regex("[^0-9]"), "")

        return when {
            digits.startsWith("254") -> digits
            digits.startsWith("0") -> "254${digits.substring(1)}"
            digits.startsWith("7") || digits.startsWith("1") -> "254$digits"
            else -> digits
        }
    }

    override fun processCallback(callbackData: StkCallbackResponse): Boolean {
        println("=== CALLBACK ENDPOINT REACHED ===")
        return try {
            val callback = callbackData.body.stkCallback
            println("=== M-PESA CALLBACK PROCESSING ===")
            println("Processing callback with result code: ${callback.resultCode}")
            println("Result description: ${callback.resultDesc}")
            println("Checkout Request ID: ${callback.checkoutRequestID}")
            println("Merchant Request ID: ${callback.merchantRequestID}")

            var mpesaReceiptNumber: String? = null
            var transactionDate: LocalDateTime? = null
            var amount: BigDecimal? = null
            var phoneNumber: String? = null

            val cancellationCodes = setOf(1032, 1031, 1037)
            val isUserCancellation = cancellationCodes.contains(callback.resultCode)

            if (callback.resultCode == 0) {
                println("Payment has been made successfully!")

                if (callback.callbackMetadata == null) {
                    println("WARNING: Callback metadata is null even though result code is 0")
                } else {
                    val metadataMap = callback.callbackMetadata.item.associateBy { it.name }
                    println("Available metadata items: ${metadataMap.keys}")

                    metadataMap["MpesaReceiptNumber"]?.value?.let { value ->
                        mpesaReceiptNumber = value.toString()
                        println("Extracted M-Pesa receipt number: $mpesaReceiptNumber")
                    }

                    metadataMap["TransactionDate"]?.value?.let { value ->
                        println("Raw transaction date value type: ${value::class.java.name}")
                        println("Raw transaction date value: $value")

                        try {
                            val dateLong = when (value) {
                                is Number -> value.toLong()
                                is String -> value.toLongOrNull() ?: throw IllegalArgumentException("Invalid TransactionDate string: $value")
                                else -> throw IllegalArgumentException("Unsupported TransactionDate type: ${value::class.java.name}")
                            }
                            val dateStr = String.format("%014d", dateLong)
                            println("Normalized date string: $dateStr")

                            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                            transactionDate = LocalDateTime.parse(dateStr, formatter)
                            println("Successfully parsed transaction date: $transactionDate")
                        } catch (e: Exception) {
                            println("Failed to parse TransactionDate: $value - ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    metadataMap["Amount"]?.value?.toString()?.toBigDecimalOrNull()?.let {
                        amount = it
                        println("Extracted amount: $amount")
                    }

                    metadataMap["PhoneNumber"]?.value?.toString()?.let {
                        phoneNumber = it
                        println("Extracted phone number: $phoneNumber")
                    }
                }
            } else if (isUserCancellation) {
                println("Payment was CANCELLED by user. Result code: ${callback.resultCode}")
            } else {
                println("Payment FAILED with code ${callback.resultCode}: ${callback.resultDesc}")
                println("Skipping metadata extraction for failed payment")
            }

            println("Updating transaction for checkout request ID: ${callback.checkoutRequestID}")
            val updatedTransaction = updateTransactionFromCallback(
                checkoutRequestId = callback.checkoutRequestID,
                resultCode = callback.resultCode.toString(),
                resultDesc = callback.resultDesc,
                mpesaReceiptNumber = mpesaReceiptNumber,
                transactionDate = transactionDate
            )

            println("Transaction update result: $updatedTransaction")
            if (updatedTransaction == null) {
                println("WARNING: No transaction found with CheckoutRequestID: ${callback.checkoutRequestID}")
                return false
            }

            runBlocking {
                println("Looking up meter payment for transaction ID: ${updatedTransaction.id}")
                val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(updatedTransaction.id)

                if (meterPayment != null) {
                    val newStatus = if (callback.resultCode == 0) "COMPLETED" else "FAILED"
                    println("Found meter payment: ${meterPayment.id}, updating status to $newStatus")

                    meterPaymentRepository.updatePaymentStatus(
                        paymentId = meterPayment.id,
                        status = newStatus
                    )
                    println("Payment status updated successfully to $newStatus")
                } else {
                    println("WARNING: No meter payment found for transaction ID: ${updatedTransaction.id}")
                }
            }

            println("=== CALLBACK PROCESSING COMPLETED SUCCESSFULLY ===")
            true
        } catch (e: Exception) {
            println("=== CALLBACK PROCESSING FAILED ===")
            println("Error processing M-Pesa callback: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    override suspend fun queryTransactionStatus(checkoutRequestId: String): Boolean {
        try {
            println("Querying transaction status from M-Pesa for checkout request ID: $checkoutRequestId")
            val token = getAccessToken()
            val mpesaConfig = getMpesaConfig()

            val localTransaction = getTransactionByCheckoutRequestId(checkoutRequestId)
            if (localTransaction == null) {
                println("No local transaction found with checkout request ID: $checkoutRequestId")
                return false
            }

            val timestamp = generateTimestamp()
            val password = generateMpesaPassword(mpesaConfig.shortCode, mpesaConfig.passkey, timestamp)

            val queryRequest = mapOf(
                "BusinessShortCode" to mpesaConfig.shortCode,
                "Password" to password,
                "Timestamp" to timestamp,
                "CheckoutRequestID" to checkoutRequestId
            )

            val response: HttpResponse = httpClient.post("${mpesaConfig.baseUrl}/mpesa/stkpushquery/v1/query") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(gson.toJson(queryRequest))
                timeout {
                    requestTimeoutMillis = 30000
                }
            }

            val responseBody = response.bodyAsText()
            println("M-Pesa query response: $responseBody")

            val queryResponse = gson.fromJson(responseBody, Map::class.java)
            val resultCode = queryResponse["ResultCode"]?.toString()
            val resultDesc = queryResponse["ResultDesc"]?.toString()

            if (resultCode == "0") {
                println("Transaction query successful: $resultDesc")

                val currentTransaction = getTransactionByCheckoutRequestId(checkoutRequestId)
                if (currentTransaction?.status == "PENDING") {
                    println("Updating transaction from query response")
                    val updatedTransaction = updateTransactionFromQuery(
                        checkoutRequestId = checkoutRequestId,
                        resultCode = resultCode,
                        resultDesc = resultDesc ?: "Transaction completed successfully"
                    )

                    updatedTransaction?.let { transaction ->
                        val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(transaction.id)

                        if (meterPayment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                paymentId = meterPayment.id,
                                status = "COMPLETED"
                            )
                            println("Updated payment status to COMPLETED from query")
                        }
                    }
                }

                return true
            } else if (resultCode != null) {
                println("Transaction query returned failure: $resultDesc")

                val currentTransaction = getTransactionByCheckoutRequestId(checkoutRequestId)
                if (currentTransaction?.status == "PENDING") {
                    println("Updating transaction status to FAILED from query")
                    val updatedTransaction = updateTransactionFromQuery(
                        checkoutRequestId = checkoutRequestId,
                        resultCode = resultCode,
                        resultDesc = resultDesc ?: "Transaction failed"
                    )

                    updatedTransaction?.let { transaction ->
                        val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(transaction.id)

                        if (meterPayment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                paymentId = meterPayment.id,
                                status = "FAILED"
                            )
                            println("Updated payment status to FAILED from query")
                        }
                    }
                }
            }

            return false
        } catch (e: Exception) {
            println("Error querying transaction from M-Pesa: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    override fun handleStalledTransactions(timeoutMinutes: Int): Int {
        println("Checking for stalled M-Pesa transactions...")
        val cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes.toLong())

        val stalledTransactions = getPendingTransactionsOlderThan(cutoffTime)

        if (stalledTransactions.isEmpty()) {
            println("No stalled transactions found")
            return 0
        }

        println("Found ${stalledTransactions.size} stalled transactions")
        var updatedCount = 0

        runBlocking {
            stalledTransactions.forEach { transaction ->
                println("Processing stalled transaction: ${transaction.id}, checkout request ID: ${transaction.checkoutRequestId}")

                try {
                    val updatedTransaction = transaction.checkoutRequestId?.let {
                        updateTransactionFromTimeout(
                            checkoutRequestId = it,
                            resultDesc = "Transaction timed out waiting for callback"
                        )
                    }

                    if (updatedTransaction != null) {
                        val payment = meterPaymentRepository.getPaymentsByMpesaTransactionId(updatedTransaction.id)

                        if (payment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                paymentId = payment.id,
                                status = "FAILED"
                            )
                            println("Updated payment ${payment.id} status to FAILED due to timeout")
                        }

                        updatedCount++
                    }
                } catch (e: Exception) {
                    println("Error handling stalled transaction ${transaction.id}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        println("Updated $updatedCount stalled transactions")
        return updatedCount
    }

    override fun createTransaction(
        amount: BigDecimal,
        phoneNumber: String,
        merchantRequestId: String?,
        checkoutRequestId: String?,
        responseCode: String?,
        responseDescription: String?,
        customerMessage: String?
    ): MpesaTransactionDto = transaction {
        val now = LocalDateTime.now()
        val mpesaTransaction = MpesaTransaction.Companion.new {
            this.merchantRequestId = merchantRequestId
            this.checkoutRequestId = checkoutRequestId
            this.responseCode = responseCode
            this.responseDescription = responseDescription
            this.customerMessage = customerMessage
            this.amount = amount
            this.phoneNumber = phoneNumber
            this.status = "PENDING"
            this.callbackReceived = false
            this.createdAt = now
            this.updatedAt = now
        }
        mpesaTransaction.toDto()
    }

    override fun updateTransactionFromCallback(
        checkoutRequestId: String,
        resultCode: String,
        resultDesc: String,
        mpesaReceiptNumber: String?,
        transactionDate: LocalDateTime?
    ): MpesaTransactionDto? = transaction {
        println("Looking for transaction with checkoutRequestId: $checkoutRequestId")
        val mpesaTransaction = MpesaTransaction.Companion.find {
            MpesaTransactions.checkoutRequestId eq checkoutRequestId
        }.firstOrNull()

        if (mpesaTransaction == null) {
            println("No transaction found with checkoutRequestId: $checkoutRequestId")
            return@transaction null
        }
        println("Found transaction: ${mpesaTransaction.id}")

        mpesaTransaction.let {
            it.responseCode = resultCode
            it.responseDescription = resultDesc
            it.mpesaReceiptNumber = mpesaReceiptNumber
            it.transactionDate = transactionDate
            it.status = when (resultCode) {
                "0" -> "SUCCESS"
                else -> "FAILED"
            }
            it.callbackReceived = true
            it.updatedAt = LocalDateTime.now()
            it.toDto()
        }
    }

    override fun updateTransactionFromTimeout(
        checkoutRequestId: String,
        resultDesc: String
    ): MpesaTransactionDto? = transaction {
        val mpesaTransaction = MpesaTransaction.Companion.find {
            MpesaTransactions.checkoutRequestId eq checkoutRequestId
        }.firstOrNull()

        if (mpesaTransaction == null) {
            return@transaction null
        }

        mpesaTransaction.let {
            it.responseCode = "1037"
            it.responseDescription = resultDesc
            it.status = "FAILED"
            it.updatedAt = LocalDateTime.now()
            it.toDto()
        }
    }

    override fun updateTransactionFromQuery(
        checkoutRequestId: String,
        resultCode: String,
        resultDesc: String
    ): MpesaTransactionDto? = transaction {
        val mpesaTransaction = MpesaTransaction.Companion.find {
            MpesaTransactions.checkoutRequestId eq checkoutRequestId
        }.firstOrNull()

        if (mpesaTransaction == null) {
            return@transaction null
        }

        mpesaTransaction.let {
            it.responseCode = resultCode
            it.responseDescription = resultDesc
            it.status = when (resultCode) {
                "0" -> "SUCCESS"
                else -> "FAILED"
            }
            it.updatedAt = LocalDateTime.now()
            it.toDto()
        }
    }

    override fun getTransactionById(id: UUID): MpesaTransactionDto? = transaction {
        MpesaTransaction.Companion.findById(id)?.toDto()
    }

    override fun getTransactionByCheckoutRequestId(checkoutRequestId: String): MpesaTransactionDto? = transaction {
        MpesaTransaction.Companion.find {
            MpesaTransactions.checkoutRequestId eq checkoutRequestId
        }.firstOrNull()?.toDto()
    }

    override fun getTransactionsByPhoneNumber(phoneNumber: String): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.Companion.find {
            MpesaTransactions.phoneNumber eq phoneNumber
        }.map { it.toDto() }
    }

    override fun getTransactionsByStatus(status: String): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.Companion.find {
            MpesaTransactions.status eq status
        }.map { it.toDto() }
    }

    override fun getPendingTransactionsOlderThan(cutoffTime: LocalDateTime): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.Companion.find {
            (MpesaTransactions.status eq "PENDING") and
                    (MpesaTransactions.callbackReceived eq false) and
                    (MpesaTransactions.createdAt less cutoffTime)
        }.map { it.toDto() }
    }

    override fun getAllTransactions(): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.Companion.all().map { it.toDto() }
    }
}