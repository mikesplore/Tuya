package com.mike

import com.mike.tuya.config.getTuyaConfig
import com.mike.tuya.model.*
import com.mike.tuya.service.SmartMeterService
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable
data class AddBalanceRequest(
    val amount: Double? = null
)

@Serializable
data class DeviceListResponse(
    val devices: List<Device>,
    val count: Int
)

@Serializable
data class CustomCommandRequest(
    val code: String,
    val value: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class SetCurrentReadingRequest(
    val reading: Double
)

@Serializable
data class SetUnitsRequest(
    val units: Int
)

@Serializable
data class SetBatteryRequest(
    val percentage: Int
)

@Serializable
data class SetStatusRequest(
    val status: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

fun Application.configureRouting() {
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", cause.message ?: "Unknown error")
            )
        }
    }
    
    val tuyaConfig = getTuyaConfig()
    val smartMeterService = SmartMeterService(
        accessId = tuyaConfig.accessId,
        accessSecret = tuyaConfig.accessSecret,
        endpoint = tuyaConfig.endpoint,
        projectCode = tuyaConfig.projectCode
    )
    
    routing {
        get("/") {
            call.respondText("Tuya Smart Meter API - Ktor Backend")
        }
        
        get("/health") {
            try {
                val connected = smartMeterService.connect()
                if (connected) {
                    call.respond(mapOf("status" to "healthy", "tuya_connection" to "connected"))
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("status" to "unhealthy", "tuya_connection" to "failed")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("connection_error", e.message ?: "Failed to connect to Tuya Cloud")
                )
            }
        }
        
        route("/api/v1") {
            // List all devices
            get("/devices") {
                try {
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@get
                    }
                    
                    val devices = smartMeterService.listAllDevices()
                    println("📱 Found ${devices.size} devices")
                    call.respond(DeviceListResponse(devices = devices, count = devices.size))
                } catch (e: Exception) {
                    println("❌ Error in /devices endpoint: ${e.message}")
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("devices_error", e.message ?: "Failed to get devices")
                    )
                }
            }
            
            // Get device details
            get("/devices/{deviceId}") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@get
                }
                
                try {
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@get
                    }
                    
                    val deviceInfo = smartMeterService.getDeviceDetails(deviceId)
                    if (deviceInfo != null) {
                        call.respond(deviceInfo)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("device_not_found", "Device $deviceId not found")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("device_error", e.message ?: "Failed to get device details")
                    )
                }
            }
            
            // Add balance to device
            post("/devices/{deviceId}/add-balance") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<AddBalanceRequest>()
                    
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@post
                    }
                    
                    val result = smartMeterService.addBalance(deviceId, request.amount)
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("command_error", e.message ?: "Failed to add balance")
                    )
                }
            }
            
            // Send custom command to device
            post("/devices/{deviceId}/commands") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<CustomCommandRequest>()
                    
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@post
                    }
                    
                    val result = smartMeterService.sendCustomCommand(deviceId, request.code, request.value)
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("command_error", e.message ?: "Failed to send command")
                    )
                }
            }
            
            // Set current reading
            post("/devices/{deviceId}/current-reading") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<SetCurrentReadingRequest>()
                    
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@post
                    }
                    
                    val result = smartMeterService.setCurrentReading(deviceId, request.reading)
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("command_error", e.message ?: "Failed to set current reading")
                    )
                }
            }
            
            // Set units remaining
            post("/devices/{deviceId}/units-remaining") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<SetUnitsRequest>()
                    
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@post
                    }
                    
                    val result = smartMeterService.setUnitsRemaining(deviceId, request.units)
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("command_error", e.message ?: "Failed to set units remaining")
                    )
                }
            }
            
            // Set battery percentage
            post("/devices/{deviceId}/battery") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<SetBatteryRequest>()
                    
                    if (request.percentage < 0 || request.percentage > 100) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("invalid_percentage", "Battery percentage must be between 0 and 100")
                        )
                        return@post
                    }
                    
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@post
                    }
                    
                    val result = smartMeterService.setBattery(deviceId, request.percentage)
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("command_error", e.message ?: "Failed to set battery percentage")
                    )
                }
            }
            
            // Set device status
            post("/devices/{deviceId}/status") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_device_id", "Device ID is required")
                    )
                    return@post
                }
                
                try {
                    val request = call.receive<SetStatusRequest>()
                    
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                        )
                        return@post
                    }
                    
                    val result = smartMeterService.setDeviceStatus(deviceId, request.status)
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("command_error", e.message ?: "Failed to set device status")
                    )
                }
            }
        }
    }
}
