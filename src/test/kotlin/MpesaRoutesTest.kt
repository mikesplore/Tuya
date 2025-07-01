package com.mike

import com.mike.database.repository.MeterRepository
import com.mike.database.repository.UserRepository
import com.mike.routes.PaymentRequest
import com.mike.tuya.service.MpesaService
import com.mike.tuya.service.StkPushResponse
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpesaRoutesTest {

    private lateinit var userRepository: UserRepository
    private lateinit var meterRepository: MeterRepository
    private lateinit var mpesaPaymentRepository: MpesaPaymentRepository
    private lateinit var meterTopUpRepository: MeterTopUpRepository
    private lateinit var mpesaService: MpesaService
    
    // Sample data for testing
    private val testUserId = UUID.randomUUID().toString()
    private val testMeterId = UUID.randomUUID().toString()
    private val testPaymentId = UUID.randomUUID().toString()
    private val testPhoneNumber = "254712345678"
    private val testAmount = BigDecimal.valueOf(100.0)
    
    @Before
    fun setup() {
        // Connect to in-memory database
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        
        // Setup repositories with mocks
        userRepository = mockk()
        meterRepository = mockk()
        mpesaPaymentRepository = mockk()
        meterTopUpRepository = mockk()
        mpesaService = mockk()
        
        // Setup common mock behaviors
        every { userRepository.findById(any()) } returns com.mike.database.entities.UserDto(
            id = testUserId,
            email = "test@example.com",
            role = "USER",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { meterRepository.findById(any()) } returns com.mike.database.entities.MeterDto(
            id = testMeterId,
            deviceId = "test-device-id",
            name = "Test Meter",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `test initiate payment endpoint`() = testApplication {
        // Configure the test application
        environment {
            config = MapApplicationConfig(
                "mpesa.consumer_key" to "test_consumer_key",
                "mpesa.consumer_secret" to "test_consumer_secret",
                "mpesa.base_url" to "https://sandbox.safaricom.co.ke",
                "mpesa.business_short_code" to "174379",
                "mpesa.passkey" to "test_passkey",
                "mpesa.callback_url" to "http://localhost:8081/payments/callback"
            )
        }
        
        // Mock behavior for the payment flow
        every { 
            mpesaPaymentRepository.createPayment(any(), any(), any(), any()) 
        } returns com.mike.database.entities.MpesaPaymentDto(
            id = testPaymentId,
            userId = testUserId,
            meterId = testMeterId,
            phoneNumber = testPhoneNumber,
            amount = testAmount,
            status = "PENDING",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { mpesaPaymentRepository.updateMerchantRequestId(any(), any()) } returns true
        every { mpesaPaymentRepository.updateCheckoutRequestId(any(), any()) } returns true
        
        coEvery { 
            mpesaService.initiateSTKPush(any(), any(), any(), any()) 
        } returns StkPushResponse(
            MerchantRequestID = "test-merchant-id",
            CheckoutRequestID = "test-checkout-id",
            ResponseCode = "0",
            ResponseDescription = "Success",
            CustomerMessage = "Success"
        )
        
        // Set up a JWT token for authentication
        val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJ1c2VySWQiOiIkdGVzdFVzZXJJZCIsImV4cCI6OTk5OTk5OTk5OX0." +
                "signature"
        
        // Test the endpoint
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
        
        val response = client.post("/payments/initiate") {
            header(HttpHeaders.Authorization, "Bearer $jwtToken")
            contentType(ContentType.Application.Json)
            setBody(PaymentRequest(
                meterId = testMeterId,
                amount = testAmount.toDouble(),
                phoneNumber = testPhoneNumber
            ))
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Verify the payment flow was executed correctly
        verify { mpesaPaymentRepository.createPayment(any(), any(), any(), any()) }
        coVerify { mpesaService.initiateSTKPush(any(), any(), any(), any()) }
        verify { mpesaPaymentRepository.updateMerchantRequestId(any(), any()) }
        verify { mpesaPaymentRepository.updateCheckoutRequestId(any(), any()) }
    }
    
    @Test
    fun `test payment status endpoint`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "mpesa.consumer_key" to "test_consumer_key",
                "mpesa.consumer_secret" to "test_consumer_secret",
                "mpesa.base_url" to "https://sandbox.safaricom.co.ke",
                "mpesa.business_short_code" to "174379",
                "mpesa.passkey" to "test_passkey",
                "mpesa.callback_url" to "http://localhost:8081/payments/callback"
            )
        }
        
        // Mock behavior for checking payment status
        every { 
            mpesaPaymentRepository.findById(testPaymentId) 
        } returns com.mike.database.entities.MpesaPaymentDto(
            id = testPaymentId,
            userId = testUserId,
            meterId = testMeterId,
            phoneNumber = testPhoneNumber,
            amount = testAmount,
            status = "COMPLETED",
            checkoutRequestId = "test-checkout-id",
            mpesaReceiptNumber = "MPESA123456",
            transactionDate = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        // Set up a JWT token for authentication
        val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJ1c2VySWQiOiIkdGVzdFVzZXJJZCIsImV4cCI6OTk5OTk5OTk5OX0." +
                "signature"
        
        // Test the endpoint
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
        
        val response = client.get("/payments/status/$testPaymentId") {
            header(HttpHeaders.Authorization, "Bearer $jwtToken")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Verify the payment status check was executed correctly
        verify { mpesaPaymentRepository.findById(testPaymentId) }
    }
    
    @Test
    fun `test payment callback endpoint`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "mpesa.consumer_key" to "test_consumer_key",
                "mpesa.consumer_secret" to "test_consumer_secret",
                "mpesa.base_url" to "https://sandbox.safaricom.co.ke",
                "mpesa.business_short_code" to "174379",
                "mpesa.passkey" to "test_passkey",
                "mpesa.callback_url" to "http://localhost:8081/payments/callback"
            )
        }
        
        // Mock behavior for handling callback
        every { 
            mpesaPaymentRepository.findByCheckoutRequestId("test-checkout-id") 
        } returns com.mike.database.entities.MpesaPaymentDto(
            id = testPaymentId,
            userId = testUserId,
            meterId = testMeterId,
            phoneNumber = testPhoneNumber,
            amount = testAmount,
            status = "PENDING",
            checkoutRequestId = "test-checkout-id",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { 
            mpesaPaymentRepository.updatePaymentStatus(
                id = any(),
                status = any(),
                resultCode = any(),
                resultDesc = any(),
                mpesaReceiptNumber = any(),
                transactionDate = any()
            ) 
        } returns com.mike.database.entities.MpesaPaymentDto(
            id = testPaymentId,
            userId = testUserId,
            meterId = testMeterId,
            phoneNumber = testPhoneNumber,
            amount = testAmount,
            status = "COMPLETED",
            checkoutRequestId = "test-checkout-id",
            mpesaReceiptNumber = "MPESA123456",
            resultCode = "0",
            resultDesc = "Success",
            transactionDate = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { 
            meterTopUpRepository.createTopUp(
                meterId = any(),
                paymentId = any(),
                unitsAdded = any()
            ) 
        } returns com.mike.database.entities.MeterTopUpDto(
            id = UUID.randomUUID().toString(),
            meterId = testMeterId,
            paymentId = testPaymentId,
            unitsAdded = testAmount,
            status = "PENDING",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        // Test the endpoint
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
        
        val callbackJson = """
        {
            "Body": {
                "stkCallback": {
                    "MerchantRequestID": "test-merchant-id",
                    "CheckoutRequestID": "test-checkout-id",
                    "ResultCode": 0,
                    "ResultDesc": "The service request is processed successfully.",
                    "CallbackMetadata": {
                        "Item": [
                            {
                                "Name": "Amount",
                                "Value": "100.00"
                            },
                            {
                                "Name": "MpesaReceiptNumber",
                                "Value": "MPESA123456"
                            },
                            {
                                "Name": "TransactionDate",
                                "Value": "20250701120000"
                            },
                            {
                                "Name": "PhoneNumber",
                                "Value": "254712345678"
                            }
                        ]
                    }
                }
            }
        }
        """
        
        val response = client.post("/payments/callback") {
            contentType(ContentType.Application.Json)
            setBody(callbackJson)
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Verify the callback handling was executed correctly
        verify { mpesaPaymentRepository.findByCheckoutRequestId("test-checkout-id") }
        verify { 
            mpesaPaymentRepository.updatePaymentStatus(
                id = any(),
                status = any(),
                resultCode = any(),
                resultDesc = any(),
                mpesaReceiptNumber = any(),
                transactionDate = any()
            ) 
        }
        verify { 
            meterTopUpRepository.createTopUp(
                meterId = any(),
                paymentId = any(),
                unitsAdded = any()
            ) 
        }
    }
}
