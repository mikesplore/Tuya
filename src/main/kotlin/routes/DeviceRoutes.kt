package com.mike.routes

import com.mike.database.repository.MeterRepository
import com.mike.database.repository.UserMeterAssignmentRepository
import com.mike.tuya.service.SmartMeterService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.launch
import java.util.*

data class DeviceListResponse(
    val devices: List<Any>,
    val count: Int
)

data class DeviceCreateRequest(
    val deviceId: String,
    val name: String,
    val productName: String? = null,
    val description: String? = null,
    val location: String? = null
)

data class DeviceUpdateRequest(
    val name: String? = null,
    val productName: String? = null,
    val description: String? = null,
    val location: String? = null,
    val active: Boolean? = null
)

data class DeviceUserAssignmentRequest(
    val userId: String
)

data class AddBalanceRequest(
    val amount: Double
)

data class CustomCommandRequest(
    val code: String,
    val value: JsonElement
)

data class ErrorResponse(
    val error: String,
    val message: String
)

fun Route.deviceRoutes(
    meterRepository: MeterRepository,
    userMeterAssignmentRepository: UserMeterAssignmentRepository,
    smartMeterService: SmartMeterService
) {
    
    // Get all devices (respond with DB first, then update from cloud in background)
    get("/devices") {
        try {
            // Respond immediately with local database devices
            val dbDevices = meterRepository.getAllMeters()
            call.respond(DeviceListResponse(devices = dbDevices, count = dbDevices.size))

            // Launch background coroutine to fetch from cloud and update DB
            launch {
                try {
                    val connected = smartMeterService.connect()
                    if (!connected) {
                        println("❌ Failed to connect to Tuya Cloud for background sync")
                        return@launch
                    }
                    val cloudDevices = smartMeterService.listAllDevices()
                    println("📱 (Background) Fetched ${cloudDevices.size} devices from Tuya Cloud")
                    for (device in cloudDevices) {
                        val existingMeter = meterRepository.findByDeviceId(device.id)
                        if (existingMeter == null) {
                            meterRepository.createMeter(
                                deviceId = device.id,
                                name = device.name ?: "Smart Meter ${device.id}",
                                productName = device.productName,
                                description = "Auto-synced from Tuya Cloud",
                                location = null
                            )
                            println("✅ (Background) Created new device for: ${device.id}")
                        } else {
                            meterRepository.updateMeter(
                                id = existingMeter.id,
                                name = device.name ?: existingMeter.name,
                                productName = device.productName ?: existingMeter.productName,
                                description = existingMeter.description,
                                location = existingMeter.location,
                                active = existingMeter.active
                            )
                            println("🔄 (Background) Updated existing device: ${existingMeter.id}")
                        }
                    }
                } catch (e: Exception) {
                    println("❌ (Background) Error syncing devices: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("❌ Error fetching devices: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("devices_error", e.message ?: "Failed to fetch devices")
            )
        }
    }
    
    // Get device by ID (available to all users)
    get("/devices/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        
        try {
            // Try to find in database first
            val device = meterRepository.findById(id)
            if (device == null) {
                // Not in database, try to get directly from Tuya Cloud
                val connected = smartMeterService.connect()
                if (!connected) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                    )
                    return@get
                }
                
                // Fetch device details from Tuya Cloud
                val deviceInfo = smartMeterService.getDeviceDetails(id)
                if (deviceInfo != null) {
                    // Cache the device in database
                    val newDevice = meterRepository.createMeter(
                        deviceId = deviceInfo.device.id,
                        name = deviceInfo.device.name ?: "Smart Meter ${deviceInfo.device.id}",
                        productName = deviceInfo.device.productName,
                        description = "Auto-synced from Tuya Cloud",
                        location = null
                    )
                    println("✅ Created new device for: ${deviceInfo.device.id}")
                    
                    // Return full device info from cloud
                    call.respond(deviceInfo)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
                }
                return@get
            }
            
            // Get detailed device info from Tuya Cloud
            val connected = smartMeterService.connect()
            if (!connected) {
                // If cloud connection fails, just return the cached data
                call.respond(device)
                return@get
            }
            
            val deviceInfo = smartMeterService.getDeviceDetails(device.deviceId)
            if (deviceInfo != null) {
                call.respond(deviceInfo)
            } else {
                // If cloud data not available, return cached data
                call.respond(device)
            }
            
        } catch (e: Exception) {
            println("❌ Error fetching device: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("device_error", e.message ?: "Failed to fetch device")
            )
        }
    }
    
    // Register a new device (admin only)
    post("/devices") {
        // Authentication check removed - now available to all users
        val request = call.receive<DeviceCreateRequest>()
        
        try {
            // Connect to Tuya Cloud
            val connected = smartMeterService.connect()
            if (!connected) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                )
                return@post
            }
            
            // Check if deviceId exists in Tuya
            val devices = smartMeterService.listAllDevices()
            val deviceExists = devices.any { it.id == request.deviceId }
            
            if (!deviceExists) {
                call.respond(
                    HttpStatusCode.BadRequest, 
                    ErrorResponse("device_not_found", "Device ID not found in Tuya Cloud")
                )
                return@post
            }
            
            // Check if device already exists in database
            val existingDevice = meterRepository.findByDeviceId(request.deviceId)
            if (existingDevice != null) {
                call.respond(
                    HttpStatusCode.Conflict, 
                    ErrorResponse("device_exists", "Device with this ID already exists")
                )
                return@post
            }
            
            // Create device in database
            val device = meterRepository.createMeter(
                deviceId = request.deviceId,
                name = request.name,
                productName = request.productName,
                description = request.description,
                location = request.location
            )
            
            call.respond(HttpStatusCode.Created, device)
            
        } catch (e: Exception) {
            println("❌ Error creating device: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("device_creation_error", e.message ?: "Failed to create device")
            )
        }
    }
    
    // Update device (admin only)
    put("/devices/{id}") {
        // Authentication check removed - now available to all users
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val request = call.receive<DeviceUpdateRequest>()
        
        try {
            val updatedDevice = meterRepository.updateMeter(
                id = id,
                name = request.name,
                productName = request.productName,
                description = request.description,
                location = request.location,
                active = request.active
            )
            
            if (updatedDevice != null) {
                call.respond(updatedDevice)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
            }
            
        } catch (e: Exception) {
            println("❌ Error updating device: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("device_update_error", e.message ?: "Failed to update device")
            )
        }
    }
    
    // Delete device (admin only)
    delete("/devices/{id}") {
        // Authentication check removed - now available to all users
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        
        try {
            val success = meterRepository.deleteMeter(id)
            
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Device deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
            }
            
        } catch (e: Exception) {
            println("❌ Error deleting device: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("device_deletion_error", e.message ?: "Failed to delete device")
            )
        }
    }
    
    // Add balance to device
    post("/devices/{id}/add-balance") {
        // Authentication check removed - now available to all users
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        
        try {
            // Check if device exists
            val device = meterRepository.findById(id)
            if (device == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
                return@post
            }
            
            val request = call.receive<AddBalanceRequest>()
            
            // Connect to Tuya Cloud
            val connected = smartMeterService.connect()
            if (!connected) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                )
                return@post
            }
            
            val result = smartMeterService.addBalance(device.deviceId, request.amount)
            call.respond(result)
            
        } catch (e: Exception) {
            println("❌ Error adding balance: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("command_error", e.message ?: "Failed to add balance")
            )
        }
    }
    
    // Send command to device
    post("/devices/{id}/commands") {
        // Authentication check removed - now available to all users
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        
        try {
            // Check if device exists
            val device = meterRepository.findById(id)
            if (device == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
                return@post
            }
            
            val request = call.receive<CustomCommandRequest>()
            
            // Connect to Tuya Cloud
            val connected = smartMeterService.connect()
            if (!connected) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                )
                return@post
            }
            
            val result = smartMeterService.sendCustomCommand(device.deviceId, request.code, request.value)
            call.respond(result)
            
        } catch (e: Exception) {
            println("❌ Error sending command: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("command_error", e.message ?: "Failed to send command")
            )
        }
    }
    
    // Assign device to user (admin only)
    post("/devices/{id}/assign") {
        // Authentication check removed - now available to all users
        
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val request = call.receive<DeviceUserAssignmentRequest>()
        
        try {
            val assignment = userMeterAssignmentRepository.assignMeterToUser(request.userId, id)
            call.respond(HttpStatusCode.Created, assignment)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_input", e.message ?: "Invalid input"))
        } catch (e: Exception) {
            println("❌ Error assigning device: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("assignment_error", e.message ?: "Failed to assign device to user")
            )
        }
    }
    
    // Unassign device from user (admin only)
    delete("/devices/{id}/assign/{userId}") {
        // Authentication check removed - now available to all users
        
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val userId = call.parameters["userId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        
        try {
            val success = userMeterAssignmentRepository.removeMeterFromUser(userId, id)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Device unassigned from user"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("assignment_not_found", "Assignment not found"))
            }
        } catch (e: Exception) {
            println("❌ Error unassigning device: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("unassignment_error", e.message ?: "Failed to unassign device from user")
            )
        }
    }
    
    // Get users assigned to device (admin only)
    get("/devices/{id}/users") {
        // Authentication check removed - now available to all users
        
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        
        try {
            val users = userMeterAssignmentRepository.getMeterUsers(id)
            call.respond(users)
        } catch (e: Exception) {
            println("❌ Error getting assigned users: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("user_list_error", e.message ?: "Failed to get assigned users")
            )
        }
    }
    
    // Force refresh devices from cloud (admin only)
    post("/devices/refresh") {
        // Authentication check removed - now available to all users
        try {
            // Connect to Tuya Cloud
            val connected = smartMeterService.connect()
            if (!connected) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
                )
                return@post
            }
            
            // Fetch devices from Tuya Cloud
            val cloudDevices = smartMeterService.listAllDevices()
            println("📱 Fetched ${cloudDevices.size} devices from Tuya Cloud")
            
            // Track new and updated devices
            val newDevices = mutableListOf<String>()
            val updatedDevices = mutableListOf<String>()
            
            // Sync cloud devices with local database
            for (device in cloudDevices) {
                val existingMeter = meterRepository.findByDeviceId(device.id)
                if (existingMeter == null) {
                    // Create new device in database
                    meterRepository.createMeter(
                        deviceId = device.id,
                        name = device.name ?: "Smart Meter ${device.id}",
                        productName = device.productName,
                        description = "Auto-synced from Tuya Cloud",
                        location = null
                    )
                    newDevices.add(device.id)
                    println("✅ Created new device for: ${device.id}")
                } else {
                    // Update existing device with cloud data
                    meterRepository.updateMeter(
                        id = existingMeter.id,
                        name = device.name ?: existingMeter.name,
                        productName = device.productName ?: existingMeter.productName,
                        description = existingMeter.description,
                        location = existingMeter.location,
                        active = existingMeter.active
                    )
                    updatedDevices.add(device.id)
                    println("🔄 Updated existing device: ${existingMeter.id}")
                }
            }
            
            call.respond(mapOf(
                "message" to "Device sync completed",
                "total" to cloudDevices.size,
                "new" to newDevices.size,
                "updated" to updatedDevices.size
            ))
            
        } catch (e: Exception) {
            println("❌ Error refreshing devices: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("refresh_error", e.message ?: "Failed to refresh devices")
            )
        }
    }
}
