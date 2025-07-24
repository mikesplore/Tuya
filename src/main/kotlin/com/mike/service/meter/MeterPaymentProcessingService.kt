package com.mike.service.meter

import com.mike.domain.model.meter.MeterPayment
import com.mike.domain.model.meter.MeterPaymentResponse
import com.mike.domain.model.mpesa.MpesaTransaction
import com.mike.domain.repository.meter.MeterPaymentRepository
import com.mike.domain.repository.mpesa.MpesaRepository
import com.mike.service.tuya.TuyaService
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Service responsible for handling the complete payment and meter charging flow
 */
class MeterPaymentProcessingService(
    private val meterPaymentRepository: MeterPaymentRepository,
    private val mpesaRepository: MpesaRepository,
    private val tuyaService: TuyaService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Process a completed M-Pesa payment by calculating units and charging the meter
     *
     * @param mpesaTransactionId The M-Pesa checkout request ID
     * @return A comprehensive payment response with payment and meter update details
     */
    suspend fun processCompletedPayment(mpesaTransactionId: String): MeterPaymentResponse {
        logger.info("Processing completed payment for M-Pesa transaction: $mpesaTransactionId")

        // 1. Get the payment details from the repository
        val payment = meterPaymentRepository.getPaymentsByMpesaTransactionId(mpesaTransactionId)
            ?: return MeterPaymentResponse(
                success = false,
                message = "Payment not found for transaction ID: $mpesaTransactionId"
            )

        // 2. Check if the payment is already processed
        if (payment.status != "COMPLETED") {
            return MeterPaymentResponse(
                success = false,
                message = "Payment is not completed. Current status: ${payment.status}",
                paymentId = payment.id,
                meterId = payment.meterId,
                mpesaTransactionId = payment.mpesaTransactionId,
                amount = payment.amount,
                status = payment.status
            )
        }

        // 3. Check if units are already calculated
        if (payment.unitsAdded == null || payment.unitsAdded.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("Payment ${payment.id} is marked as COMPLETED but has no units calculated")
            return MeterPaymentResponse(
                success = false,
                message = "Payment completed but no units calculated",
                paymentId = payment.id,
                meterId = payment.meterId,
                mpesaTransactionId = payment.mpesaTransactionId,
                amount = payment.amount,
                status = payment.status
            )
        }

        // 4. Get the M-Pesa transaction details for additional info
        val mpesaTransaction = mpesaRepository.getTransactionByCheckoutRequestId(mpesaTransactionId)

        // 5. Add units to the meter via Tuya API
        val unitsToAdd = payment.unitsAdded.toInt()
        logger.info("Adding $unitsToAdd units to meter ${payment.meterId}")

        val tuyaUpdateResult = tuyaService.chargeMeter(
            meterId = payment.meterId,
            amount = unitsToAdd,
            userId = payment.userId
        )

        val tuyaUpdateMessage = if (tuyaUpdateResult) {
            "Successfully added $unitsToAdd units to meter ${payment.meterId}"
        } else {
            "Failed to add units to meter via Tuya API. Will require manual processing."
        }

        logger.info(tuyaUpdateMessage)

        // 6. Return comprehensive response
        return MeterPaymentResponse(
            success = true,
            message = "Payment processed successfully${if (!tuyaUpdateResult) ", but meter update failed" else ""}",
            paymentId = payment.id,
            meterId = payment.meterId,
            mpesaTransactionId = payment.mpesaTransactionId,
            mpesaReceiptNumber = mpesaTransaction?.mpesaReceiptNumber,
            amount = payment.amount,
            unitsAdded = payment.unitsAdded,
            balanceBefore = payment.balanceBefore,
            balanceAfter = payment.balanceAfter,
            paymentDate = payment.paymentDate,
            status = payment.status,
            tuyaUpdateSuccess = tuyaUpdateResult,
            tuyaUpdateMessage = tuyaUpdateMessage
        )
    }

    /**
     * Check payment status and process it if completed
     *
     * @param mpesaTransactionId The M-Pesa checkout request ID
     * @return A comprehensive payment response with current status and processing results if applicable
     */
    suspend fun checkAndProcessPayment(mpesaTransactionId: String): MeterPaymentResponse {
        logger.info("Checking payment status for transaction: $mpesaTransactionId")

        // First query M-Pesa API to ensure we have the latest status
        val mpesaQuerySuccess = mpesaRepository.queryTransactionStatus(mpesaTransactionId)
        logger.info("M-Pesa query result: $mpesaQuerySuccess")

        // Get the payment details
        val payment = meterPaymentRepository.getPaymentsByMpesaTransactionId(mpesaTransactionId)
            ?: return MeterPaymentResponse(
                success = false,
                message = "Payment not found for transaction ID: $mpesaTransactionId"
            )

        if (payment.status == "COMPLETED") {
            // If payment is completed, process it
            logger.info("Payment is completed, proceeding with processing")
            return processCompletedPayment(mpesaTransactionId)
        }

        // Get the M-Pesa transaction details for additional info
        val mpesaTransaction = mpesaRepository.getTransactionByCheckoutRequestId(mpesaTransactionId)

        // Return current status
        return MeterPaymentResponse(
            success = false,
            message = "Payment is not yet completed. Current status: ${payment.status}",
            paymentId = payment.id,
            meterId = payment.meterId,
            mpesaTransactionId = payment.mpesaTransactionId,
            mpesaReceiptNumber = mpesaTransaction?.mpesaReceiptNumber,
            amount = payment.amount,
            unitsAdded = payment.unitsAdded,
            paymentDate = payment.paymentDate,
            status = payment.status
        )
    }

    /**
     * Handle the complete payment flow from M-Pesa STK push to meter token addition in a single operation.
     * This method will:
     * 1. Initiate the M-Pesa payment
     * 2. Wait for payment completion (with timeout)
     * 3. Calculate tokens based on the amount
     * 4. Add tokens to the meter via Tuya API
     *
     * @param amount Payment amount
     * @param phoneNumber Customer phone number
     * @param meterId Meter ID to receive tokens
     * @param userId User ID making the payment
     * @param accountReference Optional account reference
     * @param description Optional payment description
     * @param timeoutSeconds Maximum time to wait for payment completion
     * @return A comprehensive response with payment and token details
     */
    suspend fun completePayment(
        amount: BigDecimal,
        phoneNumber: String,
        meterId: String,
        userId: Int,
        accountReference: String = "MeterPayment",
        description: String = "Meter top-up payment",
        timeoutSeconds: Int = 60
    ): MeterPaymentResponse {
        logger.info("Starting complete payment flow for meter: $meterId, amount: $amount, phone: $phoneNumber")

        // 1. Initiate the M-Pesa payment
        val paymentResponse = mpesaRepository.initiatePayment(
            amount = amount,
            phoneNumber = phoneNumber,
            meterId = meterId,
            userId = userId,
            accountReference = accountReference,
            description = description
        )

        if (!paymentResponse.success) {
            logger.error("Failed to initiate M-Pesa payment: ${paymentResponse.message}")
            return MeterPaymentResponse(
                success = false,
                message = "Failed to initiate payment: ${paymentResponse.message}",
                meterId = meterId,
                amount = amount
            )
        }

        val checkoutRequestId = paymentResponse.checkoutRequestId
            ?: return MeterPaymentResponse(
                success = false,
                message = "Payment initiated but no checkout request ID received",
                meterId = meterId,
                amount = amount
            )

        logger.info("Payment initiated successfully. Checkout request ID: $checkoutRequestId")

        // 2. Wait for payment completion with timeout
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (timeoutSeconds * 1000)
        var payment: MeterPayment? = null
        var mpesaTransaction: MpesaTransaction? = null
        var completed = false
        var consecutiveRateLimitErrors = 0
        var waitTime = 3000L // Start with a 3-second delay

        while (System.currentTimeMillis() < endTime && !completed) {
            // Query M-Pesa for status updates
            try {
                // Apply increasing delay if we hit rate limits
                if (consecutiveRateLimitErrors > 0) {
                    val adaptiveWait = minOf(waitTime * (1L + consecutiveRateLimitErrors), 10000L) // Cap at 10 seconds
                    logger.info("Rate limit detected, waiting longer (${adaptiveWait/1000} seconds) before next attempt")
                    delay(adaptiveWait)
                    waitTime = adaptiveWait // For the next iteration
                }

                // Get current transaction before querying
                mpesaTransaction = mpesaRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
                payment = meterPaymentRepository.getPaymentsByMpesaTransactionId(checkoutRequestId)

                // Check if transaction already completed
                if (mpesaTransaction?.status == "SUCCESS" || payment?.status == "COMPLETED") {
                    logger.info("Payment already completed successfully")
                    completed = true
                    continue
                }

                if (mpesaTransaction == null) {
                    logger.warn("Transaction not found for checkout ID: $checkoutRequestId")
                    delay(waitTime) // Wait before retrying
                    continue
                }

                // Only query M-Pesa API if we're not hitting rate limits
                val queryResult = mpesaRepository.queryTransactionStatus(checkoutRequestId)

                // Check if we're hitting rate limits (queryTransactionStatus returns false for rate limit errors)
                if (!queryResult && mpesaTransaction.responseDescription?.contains("Spike arrest", ignoreCase = true) == true) {
                    consecutiveRateLimitErrors++
                } else {
                    consecutiveRateLimitErrors = 0
                    waitTime = 3000L // Reset the wait time for non-rate-limit cases
                }

                // Re-fetch transaction status after query
                mpesaTransaction = mpesaRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
                payment = meterPaymentRepository.getPaymentsByMpesaTransactionId(checkoutRequestId)

                // Check if transaction is completed
                if (mpesaTransaction?.status == "SUCCESS" || payment?.status == "COMPLETED") {
                    logger.info("Payment completed successfully")
                    completed = true
                } else if (mpesaTransaction?.status == "FAILED" || payment?.status == "FAILED") {
                    logger.info("Payment failed: ${mpesaTransaction?.responseDescription}")
                    return MeterPaymentResponse(
                        success = false,
                        message = "Payment failed: ${mpesaTransaction?.responseDescription}",
                        mpesaTransactionId = checkoutRequestId,
                        meterId = meterId,
                        amount = amount,
                        status = "FAILED"
                    )
                } else {
                    logger.info("Payment still pending. Waiting...")
                    delay(waitTime)
                }
            } catch (e: Exception) {
                logger.error("Error querying payment status: ${e.message}")
                delay(waitTime)
            }
        }

        // If timeout reached and payment not completed
        if (!completed) {
            logger.info("Payment timeout reached. Payment may still complete later.")
            return MeterPaymentResponse(
                success = false,
                message = "Payment processing timeout. The payment may still complete later. " +
                        "Check status endpoint or wait for callback.",
                mpesaTransactionId = checkoutRequestId,
                meterId = meterId,
                amount = amount,
                status = payment?.status ?: "PENDING"
            )
        }

        // 3. If we get here, payment is completed. Process it.
        return processCompletedPayment(checkoutRequestId)
    }
}
