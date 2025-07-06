package com.mike.routes

import com.google.gson.Gson
import com.mike.database.repository.*
import com.mike.mpesa.model.PaymentRequest
import com.mike.mpesa.model.StkCallbackResponse
import com.mike.mpesa.service.MpesaService
import database.repository.MeterPaymentRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.mpesaRoutes(
    mpesaService: MpesaService,
    userRepository: UserRepository,
    meterRepository: MeterRepository,
    userMeterAssignmentRepository: UserMeterAssignmentRepository,
    mpesaTransactionRepository: MpesaTransactionRepository,
    meterPaymentRepository: MeterPaymentRepository
) {

    route("/mpesa") {
        // Unauthenticated endpoints
        post("/callback") {
            try {
                println("Received M-Pesa callback request")
                println("Headers: ${call.request.headers.entries().joinToString()}")

                val rawBody = call.receiveText()
                println("Raw callback body: $rawBody")

                if (rawBody.isBlank()) {
                    println("Empty callback body received")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("ResultCode" to 1, "ResultDesc" to "Empty request body")
                    )
                    return@post
                }

                val gson = Gson()
                val callbackData = try {
                    gson.fromJson(rawBody, StkCallbackResponse::class.java)
                } catch (e: Exception) {
                    println("Failed to parse callback data: ${e.message}")
                    println("Invalid JSON structure received: $rawBody")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("ResultCode" to 1, "ResultDesc" to "Invalid JSON format")
                    )
                    return@post
                }

                val stkCallback = callbackData.body.stkCallback
                println("Callback for CheckoutRequestID: ${stkCallback.checkoutRequestID}")
                println("Result code: ${stkCallback.resultCode}")
                println("Result description: ${stkCallback.resultDesc}")

                mpesaService.processCallback(callbackData)

                // Always respond with success to M-Pesa to prevent retries
                call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
            } catch (e: Exception) {
                println("Error in M-Pesa callback: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
            }
        }

        // Authenticated user endpoints
        authenticate("auth-jwt") {
            // User payment routes
            route("/payments") {
                // Initiate payment
                post {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val userEmail = principal?.payload?.getClaim("email")?.asString()
                            ?: return@post call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("message" to "Invalid token")
                            )
                        val role = principal.payload.getClaim("role")?.asString()
                        val isAdmin = role == "ADMIN"

                        val user = userRepository.findByEmail(userEmail)
                            ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))

                        val paymentRequest = call.receive<PaymentRequest>()

                        // Validate meter exists and user has access
                        val meter = meterRepository.findById(paymentRequest.meterId)
                            ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "Meter not found"))

                        // Only check meter access for non-admin users
                        if (!isAdmin) {
                            val userMeterAssignment =
                                userMeterAssignmentRepository.isMeterAssignedToUser(user.id, paymentRequest.meterId)
                            if (!userMeterAssignment) {
                                return@post call.respond(
                                    HttpStatusCode.Forbidden,
                                    mapOf("message" to "You don't have access to this meter")
                                )
                            }
                        }

                        val paymentResponse = mpesaService.initiatePayment(
                            amount = paymentRequest.amount,
                            phoneNumber = paymentRequest.phoneNumber,
                            meterId = UUID.fromString(paymentRequest.meterId),
                            userId = UUID.fromString(user.id),
                            description = paymentRequest.description ?: "Meter ${meter.name} top-up"
                        )

                        if (paymentResponse.success) {
                            call.respond(HttpStatusCode.OK, paymentResponse)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, paymentResponse)
                        }

                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Payment initiation failed: ${e.message}")
                        )
                    }
                }
            }

            // M-Pesa callback endpoint (no authentication required)
            post("/callback") {
                try {
                    println("Received M-Pesa callback request")
                    println("Headers: ${call.request.headers.entries().joinToString()}")

                    // Get the raw request body as text first
                    val rawBody = call.receiveText()
                    println("Raw callback body: $rawBody")

                    // Verify that the body is a valid JSON
                    if (rawBody.isBlank()) {
                        println("Empty callback body received")
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("ResultCode" to 1, "ResultDesc" to "Empty request body")
                        )
                        return@post
                    }

                    // Parse the JSON manually using Gson
                    val gson = Gson()
                    val callbackData = try {
                        gson.fromJson(rawBody, StkCallbackResponse::class.java)
                    } catch (e: Exception) {
                        println("Failed to parse callback data: ${e.message}")
                        println("Invalid JSON structure received: $rawBody")
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("ResultCode" to 1, "ResultDesc" to "Invalid JSON format")
                        )
                        return@post
                    }

                    // Verify the callback has the expected structure
                    val stkCallback = callbackData.body.stkCallback
                    println("Callback for CheckoutRequestID: ${stkCallback.checkoutRequestID}")
                    println("Result code: ${stkCallback.resultCode}")
                    println("Result description: ${stkCallback.resultDesc}")

                    val processed = mpesaService.processCallback(callbackData)

                    if (processed) {
                        println("Callback processed successfully")
                        // Always respond with success to M-Pesa, even if we had internal processing issues
                        // This is to prevent M-Pesa from retrying the callback
                        call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
                    } else {
                        println("Callback processing failed")
                        // Still respond with success to M-Pesa, but log the failure
                        call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
                    }

                } catch (e: Exception) {
                    println("Error in M-Pesa callback: ${e.message}")
                    e.printStackTrace()
                    // Always respond with success to M-Pesa to prevent retries
                    call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
                }
            }
            // Get user's payment history
            authenticate("auth-jwt") {
                get("/payments") {
                    println("Received request for user payment history")
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val userEmail = principal?.payload?.getClaim("email")?.asString()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid token"))

                        val user = userRepository.findByEmail(userEmail)
                            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))

                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                        val payments = meterPaymentRepository.getUserPaymentHistory(UUID.fromString(user.id), limit)

                        call.respond(HttpStatusCode.OK, payments)

                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Failed to fetch payment history: ${e.message}")
                        )
                    }
                }
            }

            // Get specific payment details
            authenticate("auth-jwt") {
                get("/payments/{paymentId}") {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val userEmail = principal?.payload?.getClaim("email")?.asString()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid token"))

                        val user = userRepository.findByEmail(userEmail)
                            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))

                        val paymentId = call.parameters["paymentId"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("message" to "Payment ID required")
                            )

                        val payment = meterPaymentRepository.getPaymentById(UUID.fromString(paymentId))
                            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "Payment not found"))

                        // Check if payment belongs to the requesting user
                        if (payment.userId != user.id) {
                            return@get call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Access denied"))
                        }

                        call.respond(HttpStatusCode.OK, payment)

                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Failed to fetch payment details: ${e.message}")
                        )
                    }
                }
            }

            // Get M-Pesa transaction status
            authenticate("auth-jwt") {
                get("/status/{checkoutRequestId}") {
                    try {
                        val checkoutRequestId = call.parameters["checkoutRequestId"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("message" to "Checkout Request ID required")
                            )

                        val transaction = mpesaService.queryPaymentStatus(checkoutRequestId)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("message" to "Transaction not found")
                            )

                        call.respond(HttpStatusCode.OK, transaction)

                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Failed to fetch transaction status: ${e.message}")
                        )
                    }
                }
            }

            // Admin routes
            authenticate("auth-jwt") {
                route("/admin") {

                    // Get all transactions (admin only)
                    get("/transactions") {
                        val principal = call.principal<JWTPrincipal>()
                        val role = principal?.payload?.getClaim("role")?.asString()

                        if (role != "ADMIN") {
                            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
                            return@get
                        }

                        try {
                            val transactions = mpesaTransactionRepository.getAllTransactions()
                            call.respond(HttpStatusCode.OK, transactions)
                        } catch (e: Exception) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("message" to "Failed to fetch transactions: ${e.message}")
                            )
                        }
                    }

                    // Get all payments (admin only)
                    get("/payments") {
                        val principal = call.principal<JWTPrincipal>()
                        val role = principal?.payload?.getClaim("role")?.asString()

                        if (role != "ADMIN") {
                            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
                            return@get
                        }

                        try {
                            val payments = meterPaymentRepository.getAllPayments()
                            call.respond(HttpStatusCode.OK, payments)
                        } catch (e: Exception) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("message" to "Failed to fetch payments: ${e.message}")
                            )
                        }
                    }

                    // Check for stalled transactions and mark them as timed out (admin only)
                    post("/check-stalled-transactions") {
                        val principal = call.principal<JWTPrincipal>()
                        val role = principal?.payload?.getClaim("role")?.asString()

                        if (role != "ADMIN") {
                            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
                            return@post
                        }

                        try {
                            val timeoutMinutes = call.request.queryParameters["timeoutMinutes"]?.toIntOrNull() ?: 5
                            val updatedCount = mpesaService.handleStalledTransactions(timeoutMinutes)

                            call.respond(
                                HttpStatusCode.OK, mapOf(
                                    "success" to true,
                                    "message" to "Processed stalled transactions",
                                    "updatedCount" to updatedCount
                                )
                            )
                        } catch (e: Exception) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf(
                                    "success" to false,
                                    "message" to "Failed to process stalled transactions: ${e.message}"
                                )
                            )
                        }
                    }
                }
            }

            // Manually check payment status (for users to confirm payment)
            authenticate("auth-jwt") {
                post("/verify-payment/{checkoutRequestId}") {
                    try {
                        val checkoutRequestId = call.parameters["checkoutRequestId"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("message" to "Checkout Request ID required")
                            )

                        // First check if we already have a successful transaction
                        val existingTransaction = mpesaService.queryPaymentStatus(checkoutRequestId)

                        if (existingTransaction?.status == "SUCCESS") {
                            // Already completed successfully
                            return@post call.respond(
                                HttpStatusCode.OK, mapOf(
                                    "success" to true,
                                    "status" to "SUCCESS",
                                    "message" to "Payment was already completed successfully",
                                    "transaction" to existingTransaction
                                )
                            )
                        } else if (existingTransaction?.status == "FAILED") {
                            // Already marked as failed
                            return@post call.respond(
                                HttpStatusCode.OK, mapOf(
                                    "success" to false,
                                    "status" to "FAILED",
                                    "message" to "Payment failed: ${existingTransaction.responseDescription}",
                                    "transaction" to existingTransaction
                                )
                            )
                        }

                        // Query M-Pesa for the latest status
                        val successful = mpesaService.queryTransactionFromMpesa(checkoutRequestId)

                        // Get the updated transaction
                        val updatedTransaction = mpesaService.queryPaymentStatus(checkoutRequestId)

                        if (successful) {
                            call.respond(
                                HttpStatusCode.OK, mapOf(
                                    "success" to true,
                                    "status" to (updatedTransaction?.status ?: "SUCCESS"),
                                    "message" to "Payment verified and completed successfully",
                                    "transaction" to updatedTransaction
                                )
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.OK, mapOf(
                                    "success" to false,
                                    "status" to (updatedTransaction?.status ?: "PENDING"),
                                    "message" to "Payment could not be verified or is still pending",
                                    "transaction" to updatedTransaction
                                )
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Failed to verify payment: ${e.message}")
                        )
                    }
                }
            }
        }
    }
}
