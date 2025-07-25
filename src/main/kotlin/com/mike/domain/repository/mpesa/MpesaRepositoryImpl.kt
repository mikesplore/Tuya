package com.mike.domain.repository.mpesa

import com.google.gson.Gson
import com.mike.domain.model.meter.MeterPayment
import com.mike.domain.model.mpesa.*
import com.mike.domain.repository.meter.MeterPaymentRepository
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Duration
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
            // Direct parsing of JSON rather than relying on automatic deserialization
            val jsonObject = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)

            // Check whether this is a success or error response based on available fields
            if (jsonObject.has("ResponseCode") || jsonObject.has("MerchantRequestID") || jsonObject.has("CheckoutRequestID")) {
                // Success path
                StkPushResponse(
                    merchantRequestID = jsonObject.get("MerchantRequestID")?.asString,
                    checkoutRequestID = jsonObject.get("CheckoutRequestID")?.asString,
                    responseCode = jsonObject.get("ResponseCode")?.asString,
                    responseDescription = jsonObject.get("ResponseDescription")?.asString,
                    customerMessage = jsonObject.get("CustomerMessage")?.asString
                )
            } else if (jsonObject.has("errorCode") || jsonObject.has("errorMessage") || jsonObject.has("requestId")) {
                // Error path
                StkPushResponse(
                    requestId = jsonObject.get("requestId")?.asString,
                    errorCode = jsonObject.get("errorCode")?.asString,
                    errorMessage = jsonObject.get("errorMessage")?.asString
                )
            } else {
                // Unknown format
                println("Unrecognized M-Pesa response format: $responseBody")
                StkPushResponse(
                    responseCode = "999",
                    responseDescription = "Unrecognized response format",
                    customerMessage = "An error occurred while processing your payment request"
                )
            }
        } catch (e: Exception) {
            println("Error parsing STK push response: ${e.message}")
            e.printStackTrace()
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
                val meterPayment =
                    callback.checkoutRequestID?.let { meterPaymentRepository.getPaymentsByMpesaTransactionId(it) }

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

    /**
     * Save raw callback data directly without using the StkCallbackResponse class
     * Used as a fallback when normal deserialization fails
     */
    override fun saveRawCallback(
        checkoutRequestId: String,
        merchantRequestId: String?,
        resultCode: Int,
        resultDesc: String?,
        mpesaReceiptNumber: String?,
        amount: Double?,
        phoneNumber: String?
    ): Boolean {
        println("=== RAW CALLBACK PROCESSING ===")
        return try {
            val cancellationCodes = setOf(1032, 1031, 1037)
            val isUserCancellation = cancellationCodes.contains(resultCode)

            // Convert amount from Double? to BigDecimal?
            val amountBigDecimal = amount?.let { BigDecimal.valueOf(it) }

            if (resultCode == 0) {
                println("Payment has been made successfully! Receipt: $mpesaReceiptNumber")
            } else if (isUserCancellation) {
                println("Payment was CANCELLED by user. Result code: $resultCode")
            } else {
                println("Payment FAILED with code $resultCode: $resultDesc")
            }

            // Format transaction date (current time as we don't have the exact transaction time)
            val transactionDate = LocalDateTime.now()

            // Update the transaction in database
            updateTransactionFromCallback(
                MpesaTransactionCallbackUpdate(
                    checkoutRequestId = checkoutRequestId,
                    resultCode = resultCode.toString(),
                    resultDesc = resultDesc ?: "",
                    mpesaReceiptNumber = mpesaReceiptNumber,
                    transactionDate = transactionDate
                )
            )

            // Update related meter payment if found
            runBlocking {
                println("Looking up meter payment for transaction ID: $checkoutRequestId")
                val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(checkoutRequestId)

                if (meterPayment != null) {
                    val newStatus = if (resultCode == 0) "COMPLETED" else "FAILED"
                    println("Found meter payment: ${meterPayment.id}, updating status to $newStatus")

                    meterPaymentRepository.updatePaymentStatus(
                        meterPayment.copy(
                            status = newStatus,
                        )
                    )
                    println("Payment status updated successfully to $newStatus")
                } else {
                    println("WARNING: No meter payment found for transaction ID: $checkoutRequestId")
                }
            }

            println("=== RAW CALLBACK PROCESSING COMPLETED SUCCESSFULLY ===")
            true
        } catch (e: Exception) {
            println("=== RAW CALLBACK PROCESSING FAILED ===")
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

            // Don't query if the transaction is already in a final state
            if (localTransaction.status == "SUCCESS" || localTransaction.status == "FAILED" ||
                localTransaction.status == "N/A") {
                println("Transaction is already in final state: ${localTransaction.status}, skipping query")
                return localTransaction.status == "SUCCESS"
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
            val errorCode = queryResponse["errorCode"]?.toString()
            val errorMessage = queryResponse["errorMessage"]?.toString()

            // Handle special error: transaction does not exist
            if (errorCode == "500.001.1001" && errorMessage?.contains("transaction does not Exist") == true) {
                println("Transaction does not exist in M-Pesa: $errorMessage")
                transaction {
                    MpesaTransactions.update({ MpesaTransactions.checkoutRequestId eq checkoutRequestId }) {
                        it[status] = "N/A"
                        it[responseDescription] = errorMessage
                    }
                }

                // Update related meter payment status if exists
                localTransaction.checkoutRequestId?.let { checkoutReqId ->
                    val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(checkoutReqId)
                    if (meterPayment != null) {
                        meterPaymentRepository.updatePaymentStatus(
                            meterPayment.copy(
                                status = "N/A"
                            )
                        )
                        println("Updated payment status to N/A - Transaction does not exist")
                    }
                }

                return false
            }

            // Handle Spike arrest violation or other API errors
            if (queryResponse.containsKey("fault")) {
                val fault = queryResponse["fault"] as? Map<*, *>
                val faultString = fault?.get("faultstring")?.toString()
                println("API error occurred: $faultString")

                // Don't update status for rate limiting errors, just return and try later
                if (faultString?.contains("Spike arrest violation") == true) {
                    println("Rate limit hit. Will retry later.")
                    return false
                }

                return false
            }

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
                // Handle the "still processing" status code 4999 differently - don't mark as failed
                if (resultCode == "4999" || resultDesc?.contains("still", ignoreCase = true) == true) {
                    println("Transaction is still being processed: $resultDesc")
                    // Keep the transaction as PENDING - do not update status
                    return false
                }

                println("Transaction query returned failure: $resultDesc")

                val currentTransaction = getTransactionByCheckoutRequestId(checkoutRequestId)
                if (currentTransaction?.status == "PENDING") {
                    println("Updating transaction status to FAILED from query")
                    updateTransactionFromQuery(
                        MpesaTransactionQueryUpdate(
                            checkoutRequestId = checkoutRequestId,
                            resultCode = resultCode,
                            resultDesc = resultDesc ?: "Transaction failed",
                            receiptNumber = "",
                            transactionDate = LocalDateTime.now()
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

    @OptIn(DelicateCoroutinesApi::class)
    override fun startMpesaPendingTransactionMonitor() {
        GlobalScope.launch {
            while (true) {
                try {
                    val pendingTransactions = getTransactionsByStatus("PENDING")
                    if (pendingTransactions.isNotEmpty()) {
                        println("Found ${pendingTransactions.size} pending M-Pesa transactions. Querying status...")

                        // Process transactions one at a time with delay to avoid rate limiting
                        for (transaction in pendingTransactions) {
                            if (transaction.callbackReceived) {
                                println("Skipping transaction ${transaction.id}: callback already received")
                                continue
                            }

                            // Only query transactions that are still pending and are older than 30 seconds
                            // This gives time for callbacks to arrive naturally
                            if (transaction.createdAt.plusSeconds(30).isAfter(LocalDateTime.now())) {
                                println("Skipping transaction ${transaction.id}: too recent, waiting for callback")
                                continue
                            }

                            transaction.checkoutRequestId?.let { checkoutId ->
                                runCatching {
                                    // Check if this transaction should be queried
                                    val localTransaction = getTransactionByCheckoutRequestId(checkoutId)
                                    if (localTransaction != null &&
                                        (localTransaction.status == "SUCCESS" ||
                                         localTransaction.status == "FAILED" ||
                                         localTransaction.status == "N/A")) {
                                        println("Skipping query for transaction in final state: ${localTransaction.status}")
                                    } else {
                                        println("Querying status for transaction ID: $checkoutId")
                                        runBlocking { queryTransactionStatus(checkoutId) }

                                        // Add a small delay between API calls to avoid rate limiting
                                        delay(1000)
                                    }
                                }.onFailure {
                                    println("Failed to query status for $checkoutId: ${it.message}")
                                }
                            }
                        }
                    } else {
                        println("No pending M-Pesa transactions found.")
                    }

                    // Check for stalled transactions every 5 minutes
                    handleStalledTransactions(10)

                } catch (e: Exception) {
                    println("Error in M-Pesa transaction monitor: ${e.message}")
                }

                // Wait before checking again - 2 minutes
                delay(Duration.ofMinutes(2).toMillis())
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

    override fun getUserTransactions(userId: Int): List<MpesaTransaction> {
        return transaction {
            val meterPayments = meterPaymentRepository.getPaymentsByUserId(userId)
            val mpesaTransactionIds = meterPayments.mapNotNull { it.mpesaTransactionId }
            if (mpesaTransactionIds.isEmpty()) return@transaction emptyList()
            MpesaTransactions.selectAll().where { MpesaTransactions.checkoutRequestId inList mpesaTransactionIds }
                .map { toMpesaTransaction(it) }
        }
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
        // Check if there's an existing pending transaction for this user and meter
        // to avoid duplicate requests
        val existingPendingPayments = meterPaymentRepository.getPaymentsByMeterId(meterId).filter {
            it.status == "PENDING" && it.userId == userId &&
            // Only consider transactions created in the last 10 minutes
            it.createdAt.isAfter(LocalDateTime.now().minusMinutes(10))
        }

        if (existingPendingPayments.isNotEmpty()) {
            val pendingPayment = existingPendingPayments.first()

            // If we already have a pending transaction, return it instead of creating a new one
            return PaymentResponse(
                success = true,
                message = "Payment already initiated and is being processed",
                merchantRequestId = null,
                checkoutRequestId = pendingPayment.mpesaTransactionId,
                mpesaTransactionId = pendingPayment.mpesaTransactionId
            )
        }

        var retryCount = 0
        var lastException: Exception? = null
        // Implement exponential backoff for retries
        var backoffTimeMs = 1000L // Start with 1 second

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
                        // Create the payment record with initial status as PENDING
                        meterPaymentRepository.createPayment(
                            MeterPayment(
                                userId = userId,
                                meterId = meterId,
                                mpesaTransactionId = stkResponse.checkoutRequestID,
                                amount = amount,
                                unitsAdded = BigDecimal.ZERO, // Will be calculated on success
                                balanceBefore = BigDecimal.ZERO,
                                balanceAfter = BigDecimal.ZERO,
                                paymentDate = LocalDateTime.now(),
                                status = "PENDING",
                                description = description
                            )
                        )

                        // Log successful request initiation
                        println("Payment initiated successfully: ${stkResponse.checkoutRequestID}, amount: $amount")
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

                    // If it's a system busy error or rate limit error, we can retry with exponential backoff
                    if ((stkResponse.errorCode == "500.003.02" ||
                         stkResponse.errorMessage?.contains("Spike arrest") == true) &&
                        retryCount < maxRetries) {
                        retryCount++
                        backoffTimeMs *= 2 // Exponential backoff
                        println("System busy or rate limit error, retrying in ${backoffTimeMs / 1000} seconds... (Attempt $retryCount of $maxRetries)")
                        kotlinx.coroutines.delay(backoffTimeMs)
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
                } else {
                    throw Exception("M-Pesa API returned unexpected response format")
                }

            } catch (e: Exception) {
                lastException = e
                println("Payment attempt ${retryCount + 1} failed: ${e.message}")

                if (retryCount < maxRetries) {
                    retryCount++
                    backoffTimeMs *= 2 // Exponential backoff
                    println("Retrying in ${backoffTimeMs / 1000} seconds... (Attempt $retryCount of $maxRetries)")
                    delay(backoffTimeMs)
                } else {
                    println("All retry attempts failed")
                    break
                }
            }
        }

        // Log failure after all retries
        println("Payment initiation failed after $maxRetries retries: ${lastException?.message}")

        return PaymentResponse(
            success = false,
            message = "Payment initiation failed after $maxRetries retries: ${lastException?.message}",
            merchantRequestId = null,
            checkoutRequestId = null,
            mpesaTransactionId = null
        )
    }
}
