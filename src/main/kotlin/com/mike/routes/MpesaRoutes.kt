package com.mike.routes

import com.mike.domain.model.meter.MeterPaymentResponse
import com.mike.domain.model.mpesa.MpesaTransaction
import com.mike.domain.model.mpesa.PaymentResponse
import com.mike.domain.model.mpesa.StkCallback
import com.mike.domain.model.mpesa.StkCallbackBody
import com.mike.service.meter.MeterPaymentProcessingService
import com.mike.service.mpesa.MpesaService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.math.BigDecimal

fun Number.toBigDecimal(): BigDecimal = when (this) {
    is BigDecimal -> this
    is Long, is Int, is Short, is Byte -> BigDecimal.valueOf(this.toLong())
    is Double, is Float -> BigDecimal.valueOf(this.toDouble())
    else -> throw IllegalArgumentException("Cannot convert $this to BigDecimal")
}

/**
 * Configure the callback endpoint without authentication
 * This route needs to be installed outside the authenticated routes
 */
fun Route.mpesaCallbackRoute(mpesaService: MpesaService, meterPaymentProcessingService: MeterPaymentProcessingService) {
    post("/mpesa/callback") {
        println("Received Mpesa callback")
        try {
            val rawPayload = call.receiveText()
            application.log.info("Raw Mpesa callback payload: $rawPayload")

            // Skip serialization and use direct JSON parsing with Gson
            val gson = com.google.gson.GsonBuilder().setLenient().create()
            val jsonObject = gson.fromJson(rawPayload, com.google.gson.JsonObject::class.java)

            // Extract the required fields directly
            try {
                val body = jsonObject.getAsJsonObject("Body")
                val stkCallback = body.getAsJsonObject("stkCallback")
                val checkoutRequestId = stkCallback.get("CheckoutRequestID")?.asString
                val merchantRequestId = stkCallback.get("MerchantRequestID")?.asString
                val resultCode = stkCallback.get("ResultCode")?.asInt
                val resultDesc = stkCallback.get("ResultDesc")?.asString

                // Log extracted data
                application.log.info("Extracted callback data: checkoutRequestId=$checkoutRequestId, resultCode=$resultCode")

                if (checkoutRequestId != null && resultCode != null) {
                    // Process payment based on the extracted data
                    val success = processRawCallback(
                        checkoutRequestId = checkoutRequestId,
                        merchantRequestId = merchantRequestId,
                        resultCode = resultCode,
                        resultDesc = resultDesc,
                        callbackMetadata = stkCallback.getAsJsonObject("CallbackMetadata"),
                        mpesaService = mpesaService,
                        meterPaymentProcessingService = meterPaymentProcessingService,
                        call = call
                    )

                    if (success) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.OK, mapOf("ResultDesc" to "Processed with warnings"))
                    }
                } else {
                    application.log.error("Callback missing required fields: checkoutRequestId=$checkoutRequestId, resultCode=$resultCode")
                    call.respond(HttpStatusCode.OK, mapOf("ResultDesc" to "Missing required fields"))
                }
            } catch (e: Exception) {
                application.log.error("Error parsing JSON callback: ${e.message}", e)
                call.respond(HttpStatusCode.OK, mapOf("ResultDesc" to "Error parsing callback"))
            }
        } catch (e: Exception) {
            application.log.error("Error processing callback: ${e.message}", e)
            call.respond(HttpStatusCode.OK, mapOf("ResultDesc" to "Error processing callback"))
        }
    }
}

// New function to process raw callback data without serialization
private suspend fun processRawCallback(
    checkoutRequestId: String,
    merchantRequestId: String?,
    resultCode: Int,
    resultDesc: String?,
    callbackMetadata: com.google.gson.JsonObject?,
    mpesaService: MpesaService,
    meterPaymentProcessingService: MeterPaymentProcessingService,
    call: ApplicationCall
): Boolean {
    call.application.log.info("Processing raw callback for checkout request ID: $checkoutRequestId with result code: $resultCode")

    // Extract transaction details from metadata if success
    var mpesaReceiptNumber: String? = null
    var amount: Double? = null
    var phoneNumber: String? = null

    if (resultCode == 0 && callbackMetadata != null && callbackMetadata.has("Item")) {
        try {
            val items = callbackMetadata.getAsJsonArray("Item")
            for (i in 0 until items.size()) {
                val item = items[i].asJsonObject
                val name = item.get("Name")?.asString

                when (name) {
                    "MpesaReceiptNumber" -> mpesaReceiptNumber = item.get("Value")?.asString
                    "Amount" -> amount = item.get("Value")?.asDouble
                    "PhoneNumber" -> phoneNumber = item.get("Value")?.asString
                }
            }
        } catch (e: Exception) {
            call.application.log.error("Error extracting metadata: ${e.message}", e)
        }
    }

    // Save the transaction to the database
    val transactionSaved = mpesaService.saveRawCallback(
        checkoutRequestId = checkoutRequestId,
        merchantRequestId = merchantRequestId,
        resultCode = resultCode,
        resultDesc = resultDesc,
        mpesaReceiptNumber = mpesaReceiptNumber,
        amount = amount,
        phoneNumber = phoneNumber
    )

    // If callback was successful and indicates a completed payment, automatically try to process it
    if (transactionSaved && resultCode == 0) {
        // Process the payment asynchronously
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                val result = meterPaymentProcessingService.processCompletedPayment(checkoutRequestId)
                call.application.log.info("Auto-processed payment after callback: ${result.success}, message: ${result.message}")
            } catch (e: Exception) {
                call.application.log.error("Failed to auto-process payment after callback: ${e.message}", e)
            }
        }
    }

    return transactionSaved
}

@OptIn(DelicateCoroutinesApi::class)
fun Route.mpesaRoutes(
    mpesaService: MpesaService,
    meterPaymentProcessingService: MeterPaymentProcessingService
) {

    route("/mpesa") {
        post("/payment") {
            try {
                val body = call.receive<Map<String, Any>>()
                val amount = (body["amount"] as Number).toBigDecimal()
                val phoneNumber = body["phoneNumber"] as String
                val meterId = body["meterId"] as String
                val userId = (body["userId"] as Number).toInt()
                val accountReference = body["accountReference"] as? String ?: "MeterPayment"
                val description = body["description"] as? String ?: "Meter top-up payment"

                application.log.info("Initiating Mpesa payment: amount=$amount, phone=$phoneNumber, meter=$meterId")

                val response: PaymentResponse = mpesaService.initiatePayment(
                    amount, phoneNumber, meterId, userId, accountReference, description
                )
                call.respond(response)
            } catch (e: Exception) {
                application.log.error("Error processing payment request: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        get("/status/{checkoutRequestId}") {
            val checkoutRequestId = call.parameters["checkoutRequestId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing checkoutRequestId"))

            // Query M-Pesa API and update DB
            val updated = mpesaService.queryTransactionFromMpesa(checkoutRequestId)
            // Fetch latest transaction from DB
            val transaction: MpesaTransaction? = mpesaService.queryPaymentStatus(checkoutRequestId)
            if (transaction != null) {
                call.respond(transaction)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Transaction not found", "mpesaQuerySuccess" to updated))
            }
        }

        get("/payment/process/{checkoutRequestId}") {
            try {
                val checkoutRequestId = call.parameters["checkoutRequestId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing checkoutRequestId"))

                application.log.info("Processing payment for checkout request ID: $checkoutRequestId")

                // This will check the payment status, calculate units if needed, and attempt to add units to the meter
                val processResponse = meterPaymentProcessingService.checkAndProcessPayment(checkoutRequestId)

                if (processResponse.success) {
                    call.respond(processResponse)
                } else {
                    // Still return the response but with a more appropriate status code
                    call.respond(HttpStatusCode.OK, processResponse)
                }
            } catch (e: Exception) {
                application.log.error("Error processing payment: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    MeterPaymentResponse(
                        success = false,
                        message = "Error processing payment: ${e.message}"
                    )
                )
            }
        }


        get("/transactions") {
            try {
                val transactions = mpesaService.getAllTransactions()
                call.respond(transactions)
            } catch (e: Exception) {
                application.log.error("Error fetching transactions: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        post("/complete-payment") {
            try {
                val body = call.receive<Map<String, Any>>()
                val amount = (body["amount"] as Number).toBigDecimal()
                val phoneNumber = body["phoneNumber"] as String
                val meterId = body["meterId"] as String
                val userId = (body["userId"] as Number).toInt()
                val accountReference = body["accountReference"] as? String ?: "MeterPayment"
                val description = body["description"] as? String ?: "Meter top-up payment"
                val timeoutSeconds = (body["timeoutSeconds"] as? Number)?.toInt() ?: 60

                application.log.info("Complete payment request: amount=$amount, phone=$phoneNumber, meter=$meterId, timeout=$timeoutSeconds")

                // Process the complete payment flow
                val response = meterPaymentProcessingService.completePayment(
                    amount = amount,
                    phoneNumber = phoneNumber,
                    meterId = meterId,
                    userId = userId,
                    accountReference = accountReference,
                    description = description,
                    timeoutSeconds = timeoutSeconds
                )

                // Return a 202 status for payments that timed out but might still complete
                if (!response.success && response.status == "PENDING") {
                    call.respond(HttpStatusCode.Accepted, response)
                } else {
                    call.respond(response)
                }
            } catch (e: Exception) {
                application.log.error("Error processing complete payment request: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    MeterPaymentResponse(
                        success = false,
                        message = "Error processing payment: ${e.message}"
                    )
                )
            }
        }
    }
}