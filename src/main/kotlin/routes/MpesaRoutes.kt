package com.mike.routes

import com.mike.database.repository.MeterRepository
import com.mike.database.repository.MpesaTransactionRepository
import com.mike.database.repository.MeterPaymentRepository
import com.mike.database.repository.UserRepository
import com.mike.mpesa.model.*
import com.mike.mpesa.service.MpesaService
import io.ktor.http.*
import io.ktor.server.application.*
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
    mpesaTransactionRepository: MpesaTransactionRepository,
    meterPaymentRepository: MeterPaymentRepository
) {

    route("/mpesa") {

        // Initiate payment for meter top-up
        authenticate("auth-jwt") {
            post("/pay") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userEmail = principal?.payload?.getClaim("email")?.asString()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid token"))

                    val user = userRepository.findByEmail(userEmail)
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))

                    val paymentRequest = call.receive<PaymentRequest>()

                    // Validate meter exists and user has access
                    val meter = meterRepository.findById(paymentRequest.meterId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "Meter not found"))

                    // TODO: Add check to verify user has access to this meter

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
                val callbackData = call.receive<StkCallbackResponse>()
                val processed = mpesaService.processCallback(callbackData)

                if (processed) {
                    call.respond(HttpStatusCode.OK, mapOf("ResultCode" to 0, "ResultDesc" to "Success"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("ResultCode" to 1, "ResultDesc" to "Failed"))
                }

            } catch (e: Exception) {
                println("Error in M-Pesa callback: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("ResultCode" to 1, "ResultDesc" to "Error"))
            }
        }

        // Get user's payment history
        authenticate("auth-jwt") {
            get("/payments") {
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
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Payment ID required"))

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
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Checkout Request ID required"))

                    val transaction = mpesaService.queryPaymentStatus(checkoutRequestId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "Transaction not found"))

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
            }
        }
    }
}
