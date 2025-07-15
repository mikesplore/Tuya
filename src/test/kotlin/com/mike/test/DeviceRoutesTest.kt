package com.mike.test

import com.mike.domain.repository.meter.MeterRepository
import com.mike.com.mike.domain.repository.UserMeterAssignmentRepository
import com.mike.routes.deviceRoutes
import com.mike.tuya.service.SmartMeterService
import com.mike.domain.repository.MeterPaymentRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*
import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.mockk.*
import java.math.BigDecimal
import java.util.*

class DeviceRoutesTest {

    private val mockMeterRepository = mockk<MeterRepository>()
    private val mockUserMeterAssignmentRepository = mockk<UserMeterAssignmentRepository>()
    private val mockSmartMeterService = mockk<SmartMeterService>()
    private val mockMeterPaymentRepository = mockk<MeterPaymentRepository>()
    private val gson = Gson()
    
    @Test
    fun testGetDevices() = testApplication {
        // Mock repository response
        val devicesList = listOf(
            mapOf(
                "id" to "device-id-1", 
                "deviceId" to "tuya-device-id-1", 
                "name" to "Test Meter 1"
            ),
            mapOf(
                "id" to "device-id-2", 
                "deviceId" to "tuya-device-id-2", 
                "name" to "Test Meter 2"
            )
        )
        
        every { mockMeterRepository.getAllMeters() } returns devicesList
        
        // Configure routes
        routing {
            deviceRoutes(
                mockMeterRepository, 
                mockUserMeterAssignmentRepository, 
                mockSmartMeterService, 
                mockMeterPaymentRepository
            )
        }
        
        // Test the endpoint
        val response = client.get("/devices")
        
        // Assert response
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("devices"))
        assertTrue(responseBody.contains("Test Meter 1"))
        assertTrue(responseBody.contains("Test Meter 2"))
        
        // Verify mock called
        verify { mockMeterRepository.getAllMeters() }
    }
    
    @Test
    fun testGetDeviceById() = testApplication {
        val deviceId = "device-id-1"
        val tuyaDeviceId = "tuya-device-id-1"
        
        // Mock repository and service responses
        val device = mapOf(
            "id" to deviceId,
            "deviceId" to tuyaDeviceId,
            "name" to "Test Meter 1"
        )
        
        every { mockMeterRepository.findById(deviceId) } returns device
        every { mockSmartMeterService.connect() } returns true
        every { mockSmartMeterService.getDeviceDetails(tuyaDeviceId) } returns mockk {
            every { device } returns mockk {
                every { id } returns tuyaDeviceId
                every { name } returns "Test Meter 1"
            }
            every { status } returns emptyList()
            every { specifications } returns null
            every { summary } returns null
        }
        
        // Configure routes
        routing {
            deviceRoutes(
                mockMeterRepository, 
                mockUserMeterAssignmentRepository, 
                mockSmartMeterService, 
                mockMeterPaymentRepository
            )
        }
        
        // Test the endpoint
        val response = client.get("/devices/$deviceId")
        
        // Assert response
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Verify mocks called
        verify { mockMeterRepository.findById(deviceId) }
        verify { mockSmartMeterService.connect() }
        verify { mockSmartMeterService.getDeviceDetails(tuyaDeviceId) }
    }
    
    @Test
    fun testAddBalance() = testApplication {
        val deviceId = "device-id-1"
        val tuyaDeviceId = "tuya-device-id-1"
        val amount = 50.0
        
        // Mock repository and service responses
        val device = mapOf(
            "id" to deviceId,
            "deviceId" to tuyaDeviceId,
            "name" to "Test Meter 1"
        )
        
        every { mockMeterRepository.findById(deviceId) } returns device
        every { mockSmartMeterService.connect() } returns true
        
        every { mockSmartMeterService.getRemainingBalance(tuyaDeviceId) } returns mockk {
            every { success } returns true
            every { deviceId } returns tuyaDeviceId
            every { energyBalance } returns null
            every { moneyBalance } returns 100.0
        }
        
        every { mockSmartMeterService.addMoney(tuyaDeviceId, amount) } returns mockk {
            every { success } returns true
            every { message } returns "Added $amount money units to Test Meter 1"
            every { deviceId } returns tuyaDeviceId
            every { command } returns "charge_money"
            every { value } returns JsonPrimitive(amount)
            every { additionalData } returns mapOf(
                "balanceBefore" to 100.0,
                "balanceAfter" to 150.0,
                "unitsAdded" to 50.0
            )
        }
        
        every { 
            mockMeterPaymentRepository.createDirectPayment(
                meterId = UUID.fromString(deviceId),
                amount = BigDecimal("50.0"),
                description = "Direct balance top-up",
                balanceBefore = BigDecimal("100.0"),
                balanceAfter = any(),
                unitsAdded = BigDecimal("50.0")
            ) 
        } returns mockk {
            every { id } returns "payment-id-1"
            every { amount } returns BigDecimal("50.0")
            every { status } returns "COMPLETED"
            every { balanceBefore } returns BigDecimal("100.0")
            every { balanceAfter } returns BigDecimal("150.0")
        }
        
        // Configure routes
        routing {
            deviceRoutes(
                mockMeterRepository, 
                mockUserMeterAssignmentRepository, 
                mockSmartMeterService, 
                mockMeterPaymentRepository
            )
        }
        
        // Test the endpoint
        val response = client.post("/devices/$deviceId/add-balance") {
            contentType(ContentType.Application.Json)
            setBody("""{"amount": 50.0}""")
        }
        
        // Assert response
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("success"))
        assertTrue(responseBody.contains("payment"))
        
        // Verify mocks called
        verify { mockMeterRepository.findById(deviceId) }
        verify { mockSmartMeterService.connect() }
        verify { mockSmartMeterService.getRemainingBalance(tuyaDeviceId) }
        verify { mockSmartMeterService.addMoney(tuyaDeviceId, amount) }
        verify { 
            mockMeterPaymentRepository.createDirectPayment(
                meterId = UUID.fromString(deviceId),
                amount = BigDecimal("50.0"),
                description = "Direct balance top-up",
                balanceBefore = BigDecimal("100.0"),
                balanceAfter = any(),
                unitsAdded = BigDecimal("50.0")
            ) 
        }
    }
    
    @Test
    fun testBillingInfo() = testApplication {
        val deviceId = "device-id-1"
        val tuyaDeviceId = "tuya-device-id-1"
        
        // Mock repository and service responses
        val device = mapOf(
            "id" to deviceId,
            "deviceId" to tuyaDeviceId,
            "name" to "Test Meter 1"
        )
        
        every { mockMeterRepository.findById(deviceId) } returns device
        every { mockSmartMeterService.connect() } returns true
        
        every { mockSmartMeterService.getRemainingBalance(tuyaDeviceId) } returns mockk {
            every { success } returns true
            every { deviceId } returns tuyaDeviceId
            every { energyBalance } returns 20.0
            every { moneyBalance } returns 150.0
        }
        
        every { mockSmartMeterService.getEnergyUsage(tuyaDeviceId) } returns mockk {
            every { success } returns true
            every { deviceId } returns tuyaDeviceId
            every { totalEnergyUsed } returns 80.0
            every { totalEnergyPurchased } returns 100.0
            every { monthlyEnergy } returns 30.0
            every { dailyEnergy } returns 1.5
        }
        
        every { mockMeterPaymentRepository.getPaymentsByMeterId(UUID.fromString(deviceId)) } returns listOf(
            mockk {
                every { id } returns "payment-id-1"
                every { amount } returns BigDecimal("50.0")
                every { status } returns "COMPLETED"
                every { paymentDate } returns java.time.LocalDateTime.now()
            }
        )
        
        // Configure routes
        routing {
            deviceRoutes(
                mockMeterRepository, 
                mockUserMeterAssignmentRepository, 
                mockSmartMeterService, 
                mockMeterPaymentRepository
            )
        }
        
        // Test the endpoint
        val response = client.get("/devices/$deviceId/billing")
        
        // Assert response
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("balance"))
        assertTrue(responseBody.contains("usage"))
        assertTrue(responseBody.contains("lastPayment"))
        
        // Verify mocks called
        verify { mockMeterRepository.findById(deviceId) }
        verify { mockSmartMeterService.connect() }
        verify { mockSmartMeterService.getRemainingBalance(tuyaDeviceId) }
        verify { mockSmartMeterService.getEnergyUsage(tuyaDeviceId) }
        verify { mockMeterPaymentRepository.getPaymentsByMeterId(UUID.fromString(deviceId)) }
    }
    
    @Test
    fun testPaymentHistory() = testApplication {
        val deviceId = "device-id-1"
        
        // Mock repository responses
        val device = mapOf(
            "id" to deviceId,
            "deviceId" to "tuya-device-id-1",
            "name" to "Test Meter 1"
        )
        
        every { mockMeterRepository.findById(deviceId) } returns device
        
        every { mockMeterPaymentRepository.getPaymentsByMeterId(UUID.fromString(deviceId)) } returns listOf(
            mockk {
                every { id } returns "payment-id-1"
                every { amount } returns BigDecimal("50.0")
                every { status } returns "COMPLETED"
                every { paymentDate } returns java.time.LocalDateTime.now()
            },
            mockk {
                every { id } returns "payment-id-2"
                every { amount } returns BigDecimal("100.0")
                every { status } returns "COMPLETED"
                every { paymentDate } returns java.time.LocalDateTime.now().minusDays(1)
            }
        )
        
        // Configure routes
        routing {
            deviceRoutes(
                mockMeterRepository, 
                mockUserMeterAssignmentRepository, 
                mockSmartMeterService, 
                mockMeterPaymentRepository
            )
        }
        
        // Test the endpoint
        val response = client.get("/devices/$deviceId/payments")
        
        // Assert response
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("payments"))
        assertTrue(responseBody.contains("count"))
        assertTrue(responseBody.contains("payment-id-1"))
        assertTrue(responseBody.contains("payment-id-2"))
        
        // Verify mocks called
        verify { mockMeterRepository.findById(deviceId) }
        verify { mockMeterPaymentRepository.getPaymentsByMeterId(UUID.fromString(deviceId)) }
    }
    
    @Test
    fun testSetRate() = testApplication {
        val deviceId = "device-id-1"
        val tuyaDeviceId = "tuya-device-id-1"
        val price = 20.0
        
        // Mock repository and service responses
        val device = mapOf(
            "id" to deviceId,
            "deviceId" to tuyaDeviceId,
            "name" to "Test Meter 1"
        )
        
        every { mockMeterRepository.findById(deviceId) } returns device
        every { mockSmartMeterService.connect() } returns true
        
        every { mockSmartMeterService.setUnitPrice(tuyaDeviceId, price, "Ksh") } returns mockk {
            every { success } returns true
            every { message } returns "Command 'goods_price' sent successfully to Test Meter 1"
            every { deviceId } returns tuyaDeviceId
            every { command } returns "goods_price"
            every { value } returns JsonPrimitive(price)
        }
        
        // Configure routes
        routing {
            deviceRoutes(
                mockMeterRepository, 
                mockUserMeterAssignmentRepository, 
                mockSmartMeterService, 
                mockMeterPaymentRepository
            )
        }
        
        // Test the endpoint
        val response = client.post("/devices/$deviceId/set-rate") {
            contentType(ContentType.Application.Json)
            setBody("""{"price": 20.0, "currencySymbol": "Ksh"}""")
        }
        
        // Assert response
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("success"))
        assertTrue(responseBody.contains("message"))
        
        // Verify mocks called
        verify { mockMeterRepository.findById(deviceId) }
        verify { mockSmartMeterService.connect() }
        verify { mockSmartMeterService.setUnitPrice(tuyaDeviceId, price, "Ksh") }
    }
}
