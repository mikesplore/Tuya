package com.mike.routes

import com.mike.domain.model.mpesa.MpesaTransaction
import com.mike.domain.model.mpesa.PaymentResponse
import com.mike.service.mpesa.MpesaService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.mike.domain.model.mpesa.StkCallbackResponse
import com.mike.domain.model.mpesa.StkCallbackBody
import com.mike.domain.model.mpesa.StkCallback
import com.mike.domain.model.mpesa.CallbackMetadata
import com.mike.domain.model.mpesa.CallbackItem
import kotlinx.serialization.decodeFromString
import org.koin.ktor.ext.inject
import java.math.BigDecimal

fun Number.toBigDecimal(): BigDecimal = when (this) {
    is BigDecimal -> this
    is Long, is Int, is Short, is Byte -> BigDecimal.valueOf(this.toLong())
    is Double, is Float -> BigDecimal.valueOf(this.toDouble())
    else -> throw IllegalArgumentException("Cannot convert $this to BigDecimal")
}

fun Route.mpesaRoutes(mpesaService: MpesaService) {

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

        post("/callback") {
            println("Received Mpesa callback")
            try {
                val rawPayload = call.receiveText()
                application.log.info("Raw Mpesa callback payload: $rawPayload")
                val callbackData = try {
                    Json { ignoreUnknownKeys = true }.decodeFromString<StkCallbackResponse>(rawPayload)
                } catch (e: Exception) {
                    application.log.error("Failed to deserialize callback: ${e.message}", e)
                    null
                }
                if (callbackData?.body?.stkCallback?.checkoutRequestID == null) {
                    application.log.error("Callback missing required fields: $rawPayload")
                    call.respond(HttpStatusCode.BadRequest, mapOf("ResultCode" to 1, "ResultDesc" to "Invalid callback payload"))
                    return@post
                }
                application.log.info("Processing callback for checkout request ID: ${callbackData.body.stkCallback.checkoutRequestID}")

                val success = mpesaService.processCallback(callbackData)
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 1, "ResultDesc" to "Failed to process callback"))
                }
            } catch (e: Exception) {
                application.log.error("Error processing callback: ${e.message}", e)
                call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 1, "ResultDesc" to "Error: ${e.message}"))
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
    }
}