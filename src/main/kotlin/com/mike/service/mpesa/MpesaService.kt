//package com.mike.service.mpesa
//
//import com.mike.domain.model.meter.MeterPayment
//import com.mike.domain.model.mpesa.MpesaTransaction
//import com.mike.domain.model.mpesa.MpesaTransactionCreateRequest
//import com.mike.domain.model.mpesa.PaymentResponse
//import com.mike.domain.model.mpesa.StkCallbackResponse
//import com.mike.domain.repository.meter.MeterPaymentRepository
//import com.mike.domain.repository.mpesa.MpesaRepository
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.withContext
//import java.math.BigDecimal
//import java.time.LocalDateTime
//
//class MpesaService(
//    private val mpesaRepository: MpesaRepository,
//    private val meterPaymentRepository: MeterPaymentRepository
//) {
//    // Initiate STK Push payment with a retry mechanism
//    suspend fun initiatePayment(
//        amount: BigDecimal,
//        phoneNumber: String,
//        meterId: String,
//        userId: Int,
//        accountReference: String = "MeterPayment",
//        description: String = "Meter top-up payment",
//        maxRetries: Int = 2
//    ): PaymentResponse {
//        var retryCount = 0
//        var lastException: Exception? = null
//
//        while (retryCount <= maxRetries) {
//            try {
//                println("Initiating payment attempt ${retryCount + 1}/${maxRetries + 1}")
//
//                // Call repository to initiate STK push
//                val stkResponse = mpesaRepository.initiateStk(
//                    amount = amount,
//                    phoneNumber = phoneNumber,
//                    accountReference = accountReference,
//                    description = description
//                )
//
//                // Check if the response indicates success or a retry-able error
//                if (stkResponse.responseCode == null) {
//                    throw Exception("M-Pesa API returned null response code")
//                }
//
//                // Create M-Pesa transaction record
//                mpesaRepository.createTransaction(
//                    MpesaTransactionCreateRequest(
//                        amount = amount,
//                        phoneNumber = phoneNumber,
//                        merchantRequestId = stkResponse.merchantRequestID,
//                        checkoutRequestId = stkResponse.checkoutRequestID,
//                        responseCode = stkResponse.responseCode,
//                        responseDescription = stkResponse.responseDescription,
//                        customerMessage = stkResponse.customerMessage
//                    )
//                )
//
//                // Create a meter payment record if M-Pesa transaction was created successfully
//                val meterPayment = if (stkResponse.responseCode == "0") {
//                    // Using withContext to properly call the suspending function
//                    val payment = withContext(Dispatchers.IO) {
//                        meterPaymentRepository.createPayment(
//                            MeterPayment(
//                                userId = userId,
//                                meterId = meterId,
//                                mpesaTransactionId = mpesaTransaction.id,
//                                amount = amount,
//                                unitsAdded = 0, // This will be updated later
//                                balanceBefore = 0.0, // Placeholder, will be updated later
//                                balanceAfter = 0.0, // Placeholder, will be updated later
//                                paymentDate = LocalDateTime.now(),
//                                status = "PENDING",
//                                description = description
//                            )
//                        )
//                    }
//                    payment.id
//                } else {
//                    null
//                }
//
//                return PaymentResponse(
//                    success = stkResponse.responseCode == "0",
//                    message = stkResponse.customerMessage ?: stkResponse.responseDescription ?: "Payment initiated",
//                    merchantRequestId = stkResponse.merchantRequestID,
//                    checkoutRequestId = stkResponse.checkoutRequestID,
//                    mpesaTransactionId = meterPayment
//                )
//
//            } catch (e: Exception) {
//                lastException = e
//                println("Payment attempt ${retryCount + 1} failed: ${e.message}")
//
//                if (retryCount < maxRetries) {
//                    val backoffMs = (1000 * (retryCount + 1)).toLong() // Simple backoff strategy
//                    println("Retrying in ${backoffMs/1000} seconds...")
//                    delay(backoffMs)
//                    retryCount++
//                } else {
//                    println("All retry attempts failed")
//                    break
//                }
//            }
//        }
//
//        return PaymentResponse(
//            success = false,
//            message = "Payment initiation failed after $maxRetries retries: ${lastException?.message}",
//            merchantRequestId = null,
//            checkoutRequestId = null,
//            mpesaTransactionId = null
//        )
//    }
//
//    // Process M-Pesa callback
//    fun processCallback(callbackData: StkCallbackResponse): Boolean {
//        return mpesaRepository.processCallback(callbackData)
//    }
//
//    // Query payment status
//    fun queryPaymentStatus(checkoutRequestId: String): MpesaTransaction? {
//        return mpesaRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
//    }
//
//    // Query transaction from M-Pesa API
//    suspend fun queryTransactionFromMpesa(checkoutRequestId: String): Boolean {
//        return mpesaRepository.queryTransactionStatus(checkoutRequestId)
//    }
//
//    // Check for stalled transactions
//    fun handleStalledTransactions(timeoutMinutes: Int = 5): Int {
//        return mpesaRepository.handleStalledTransactions(timeoutMinutes)
//    }
//
//    fun getAllTransactions(): List<MpesaTransaction> {
//        return mpesaRepository.getAllTransactions()
//    }
//}