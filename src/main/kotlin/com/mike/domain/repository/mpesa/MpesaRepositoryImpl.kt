package com.mike.domain.repository.mpesa

import com.google.gson.Gson
import com.mike.domain.model.mpesa.*
import com.mike.domain.repository.meter.MeterPaymentRepository
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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

    override fun getMpesaConfig(): MpesaConfig {
        val dotenv = dotenv {
            ignoreIfMissing = true
        }
        return MpesaConfig(
            baseUrl = dotenv["MPESA_BASE_URL"],
            consumerKey = dotenv["MPESA_CONSUMER_KEY"] ?: "",
            consumerSecret = dotenv["MPESA_CONSUMER_SECRET"] ?: "",
            shortCode = dotenv["MPESA_SHORT_CODE"] ?: "",
            passkey = dotenv["MPESA_PASSKEY"] ?: "",
            callbackUrl = dotenv["MPESA_CALLBACK_URL"] ?: ""
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

        val response: HttpResponse =
            httpClient.get("${mpesaConfig.baseUrl}/oauth/v1/generate?grant_type=client_credentials") {
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

        // For testing purposes, print out the config values
        println("M-Pesa Config: shortCode=${mpesaConfig.shortCode}, baseUrl=${mpesaConfig.baseUrl}")
        println("Request details: amount=$amount, phone=$formattedPhone, timestamp=$timestamp")

        val requestUrl = "${mpesaConfig.baseUrl}/mpesa/stkpush/v1/processrequest"

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

        val requestJson = gson.toJson(stkRequest)
        println("STK Push Request JSON: $requestJson")

        val response: HttpResponse = httpClient.post(requestUrl) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody(requestJson)
            timeout {
                requestTimeoutMillis = 30000
            }
        }

        val responseBody = response.bodyAsText()
        println("M-Pesa STK push response: $responseBody")

        return try {
            gson.fromJson(responseBody, StkPushResponse::class.java)
        } catch (e: Exception) {
            println("Error parsing STK push response: ${e.message}")
            StkPushResponse(
                merchantRequestID = null,
                checkoutRequestID = null,
                responseCode = "999",
                responseDescription = "Error parsing response: ${e.message}",
                customerMessage = "An error occurred while processing your payment request"
            )
        }
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

            var mpesaReceiptNumber: String? = null
            var transactionDate: LocalDateTime? = null
            var amount: BigDecimal?
            var phoneNumber: String?

            val cancellationCodes = setOf(1032, 1031, 1037)
            val isUserCancellation = cancellationCodes.contains(callback.resultCode)

            if (callback.resultCode == 0) {
                println("Payment has been made successfully!")

                if (callback.callbackMetadata?.item != null) {
                    val metadataMap = callback.callbackMetadata.item.associateBy { it.name }
                    metadataMap["MpesaReceiptNumber"]?.value?.let { value ->
                        mpesaReceiptNumber = value.toString()
                    }

                    metadataMap["TransactionDate"]?.value?.let { value ->
                        try {
                            val dateLong = when (value) {
                                is kotlinx.serialization.json.JsonPrimitive -> value.content.toLongOrNull()
                                else -> value.toString().toLongOrNull()
                            }
                            if (dateLong != null) {
                                val dateStr = String.format("%014d", dateLong)
                                println("Normalized date string: $dateStr")
                                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                                transactionDate = LocalDateTime.parse(dateStr, formatter)
                                println("Successfully parsed transaction date: $transactionDate")
                            }
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
            }

            updateTransactionFromCallback(
                MpesaTransactionCallbackUpdate(
                    checkoutRequestId = callback.checkoutRequestID ?: "",
                    resultCode = callback.resultCode?.toString() ?: "",
                    resultDesc = callback.resultDesc ?: "",
                    mpesaReceiptNumber = mpesaReceiptNumber,
                    transactionDate = transactionDate
                )
            )

            runBlocking {
                println("Looking up meter payment for transaction ID: ${callback.checkoutRequestID}")
                val meterPayment = callback.checkoutRequestID?.let { meterPaymentRepository.getPaymentsByMpesaTransactionId(it) }

                if (meterPayment != null) {
                    val newStatus = if (callback.resultCode == 0) "COMPLETED" else "FAILED"
                    println("Found meter payment: ${meterPayment.id}, updating status to $newStatus")

                    meterPaymentRepository.updatePaymentStatus(
                        meterPayment.copy(
                            status = newStatus,
                        )
                    )
                    println("Payment status updated successfully to $newStatus")
                } else {
                    println("WARNING: No meter payment found for transaction ID: ${callback.checkoutRequestID}")
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
                println("Mpesa full response: $queryResponse")
                if (currentTransaction?.status == "PENDING") {
                    println("Updating transaction from query response")

                    updateTransactionFromQuery(
                        MpesaTransactionQueryUpdate(
                            checkoutRequestId = checkoutRequestId,
                            resultCode = resultCode,
                            resultDesc = resultDesc ?: "Transaction completed successfully",
                            receiptNumber = queryResponse["MpesaReceiptNumber"]?.toString() ?: "",
                            transactionDate = queryResponse["TransactionDate"]?.toString()?.let {
                                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                            } ?: LocalDateTime.now()
                        )
                    )

                    currentTransaction.checkoutRequestId?.let { checkoutReqId ->
                        val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(checkoutReqId)

                        if (meterPayment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                meterPayment.copy(
                                    status = "COMPLETED"
                                )
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
                    updateTransactionFromQuery(
                        MpesaTransactionQueryUpdate(
                            checkoutRequestId = checkoutRequestId,
                            resultCode = resultCode,
                            resultDesc = resultDesc ?: "Transaction failed",
                            receiptNumber = queryResponse["MpesaReceiptNumber"]?.toString() ?: "",
                            transactionDate = queryResponse["TransactionDate"]?.toString()?.let {
                                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                            } ?: LocalDateTime.now()
                        )
                    )

                    currentTransaction.checkoutRequestId?.let { checkoutReqId ->
                        val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(checkoutReqId)

                        if (meterPayment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                meterPayment.copy(
                                    status = "FAILED"
                                )
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
                    transaction.checkoutRequestId?.let {
                        updateTransactionFromTimeout(
                            MpesaTransactionTimeoutUpdate(
                                checkoutRequestId = it,
                                resultDesc = "Transaction timed out waiting for callback"
                            )
                        )
                    }

                    // Changed from Int to String
                    val payment = transaction.checkoutRequestId?.let {
                        meterPaymentRepository.getPaymentsByMpesaTransactionId(it)
                    }

                    if (payment != null) {
                        meterPaymentRepository.updatePaymentStatus(
                            payment.copy(
                                status = "FAILED"
                            )
                        )
                        println("Updated payment ${payment.id} status to FAILED due to timeout")
                    }

                    updatedCount++
                } catch (e: Exception) {
                    println("Error handling stalled transaction ${transaction.id}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        println("Updated $updatedCount stalled transactions")
        return updatedCount
    }

    override fun createTransaction(request: MpesaTransactionCreateRequest) {
        transaction {
            val now = LocalDateTime.now()
            MpesaTransactions.insert {
                it[merchantRequestId] = request.merchantRequestId
                it[checkoutRequestId] = request.checkoutRequestId
                it[responseCode] = request.responseCode
                it[responseDescription] = request.responseDescription
                it[customerMessage] = request.customerMessage
                it[amount] = request.amount
                it[phoneNumber] = request.phoneNumber
                it[status] = "PENDING"
                it[callbackReceived] = false
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    override fun updateTransactionFromCallback(request: MpesaTransactionCallbackUpdate) {
        transaction {
            val newStatus = if (request.resultCode == "0") "SUCCESS" else "FAILED"
            MpesaTransactions.update({ MpesaTransactions.checkoutRequestId eq request.checkoutRequestId }) {
                it[responseCode] = request.resultCode
                it[responseDescription] = request.resultDesc
                it[mpesaReceiptNumber] = request.mpesaReceiptNumber
                it[transactionDate] = request.transactionDate
                it[status] = newStatus
                it[callbackReceived] = true
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun updateTransactionFromTimeout(request: MpesaTransactionTimeoutUpdate) {
        transaction {
            MpesaTransactions.update({ MpesaTransactions.checkoutRequestId eq request.checkoutRequestId }) {
                it[responseCode] = "1037"
                it[responseDescription] = request.resultDesc
                it[status] = "FAILED"
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun updateTransactionFromQuery(request: MpesaTransactionQueryUpdate) {
        transaction {
            val newStatus = if (request.resultCode == "0") "SUCCESS" else "FAILED"
            MpesaTransactions.update({ MpesaTransactions.checkoutRequestId eq request.checkoutRequestId }) {
                it[responseCode] = request.resultCode
                it[responseDescription] = request.resultDesc
                it[mpesaReceiptNumber] = request.receiptNumber
                it[transactionDate] = request.transactionDate
                it[status] = newStatus
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun getTransactionById(id: Int): MpesaTransaction? = transaction {
        MpesaTransactions.selectAll().where { MpesaTransactions.id eq id }
            .singleOrNull()
            ?.let { toMpesaTransaction(it) }
    }

    override fun getTransactionByCheckoutRequestId(checkoutRequestId: String): MpesaTransaction? = transaction {
        MpesaTransactions.selectAll().where { MpesaTransactions.checkoutRequestId eq checkoutRequestId }
            .singleOrNull()
            ?.let { toMpesaTransaction(it) }
    }

    override fun getTransactionsByPhoneNumber(phoneNumber: String): List<MpesaTransaction> = transaction {
        MpesaTransactions.selectAll().where { MpesaTransactions.phoneNumber eq phoneNumber }
            .map { toMpesaTransaction(it) }
    }

    override fun getTransactionsByStatus(status: String): List<MpesaTransaction> = transaction {
        MpesaTransactions.selectAll().where { MpesaTransactions.status eq status }
            .map { toMpesaTransaction(it) }
    }

    override fun getPendingTransactionsOlderThan(cutoffTime: LocalDateTime): List<MpesaTransaction> = transaction {
        MpesaTransactions.selectAll().where {
            (MpesaTransactions.status eq "PENDING") and
                    (MpesaTransactions.callbackReceived eq false) and
                    (MpesaTransactions.createdAt less cutoffTime)
        }.map { toMpesaTransaction(it) }
    }

    override fun getAllTransactions(): List<MpesaTransaction> = transaction {
        MpesaTransactions.selectAll().map { toMpesaTransaction(it) }
    }

    override suspend fun initiatePayment(
        amount: BigDecimal,
        phoneNumber: String,
        meterId: String,
        userId: Int,
        accountReference: String,
        description: String,
        maxRetries: Int
    ): PaymentResponse {
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount <= maxRetries) {
            try {
                val stkResponse = initiateStk(
                    amount = amount,
                    phoneNumber = phoneNumber,
                    accountReference = accountReference,
                    description = description
                )

                // First check if the response contains a regular response code (success case)
                if (stkResponse.responseCode != null) {
                    // Success path - regular response
                    createTransaction(
                        MpesaTransactionCreateRequest(
                            amount = amount,
                            phoneNumber = phoneNumber,
                            merchantRequestId = stkResponse.merchantRequestID ?: "",
                            checkoutRequestId = stkResponse.checkoutRequestID ?: "",
                            responseCode = stkResponse.responseCode,
                            responseDescription = stkResponse.responseDescription,
                            customerMessage = stkResponse.customerMessage
                        )
                    )

                    if (stkResponse.responseCode == "0") {
                        meterPaymentRepository.createPayment(
                            com.mike.domain.model.meter.MeterPayment(
                                userId = userId,
                                meterId = meterId,
                                mpesaTransactionId = stkResponse.checkoutRequestID,
                                amount = amount,
                                unitsAdded = BigDecimal.ZERO,
                                balanceBefore = BigDecimal.ZERO,
                                balanceAfter = BigDecimal.ZERO,
                                paymentDate = java.time.LocalDateTime.now(),
                                status = "PENDING",
                                description = description
                            )
                        )
                    }

                    return PaymentResponse(
                        success = stkResponse.responseCode == "0",
                        message = stkResponse.customerMessage ?: stkResponse.responseDescription ?: "Payment initiated",
                        merchantRequestId = stkResponse.merchantRequestID,
                        checkoutRequestId = stkResponse.checkoutRequestID,
                        mpesaTransactionId = stkResponse.checkoutRequestID
                    )
                }
                // Check for error response format
                else if (stkResponse.errorCode != null) {
                    // Handle error response from API
                    println("Received error response from M-Pesa API: ${stkResponse.errorCode} - ${stkResponse.errorMessage}")

                    // If it's a system busy error, we can retry
                    if (stkResponse.errorCode == "500.003.02" && retryCount < maxRetries) {
                        retryCount++
                        val backoffMs = (2000 * retryCount).toLong()
                        println("System busy error, retrying in ${backoffMs/1000} seconds...")
                        kotlinx.coroutines.delay(backoffMs)
                        continue
                    }

                    // Other errors or final retry - create a failed transaction record
                    createTransaction(
                        MpesaTransactionCreateRequest(
                            amount = amount,
                            phoneNumber = phoneNumber,
                            merchantRequestId = stkResponse.requestId ?: "",
                            checkoutRequestId = "",  // No checkout ID for errors
                            responseCode = stkResponse.errorCode,
                            responseDescription = stkResponse.errorMessage,
                            customerMessage = "Payment failed"
                        )
                    )

                    return PaymentResponse(
                        success = false,
                        message = stkResponse.errorMessage ?: "Payment failed",
                        merchantRequestId = stkResponse.requestId,
                        checkoutRequestId = null,
                        mpesaTransactionId = null
                    )
                }
                else {
                    throw Exception("M-Pesa API returned unexpected response format")
                }

            } catch (e: Exception) {
                lastException = e
                println("Payment attempt ${retryCount + 1} failed: ${e.message}")

                if (retryCount < maxRetries) {
                    val backoffMs = (1000 * (retryCount + 1)).toLong()
                    println("Retrying in ${backoffMs/1000} seconds...")
                    kotlinx.coroutines.delay(backoffMs)
                    retryCount++
                } else {
                    println("All retry attempts failed")
                    break
                }
            }
        }

        return PaymentResponse(
            success = false,
            message = "Payment initiation failed after $maxRetries retries: ${lastException?.message}",
            merchantRequestId = null,
            checkoutRequestId = null,
            mpesaTransactionId = null
        )
    }
}
