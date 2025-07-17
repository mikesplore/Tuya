//package com.mike.routes
//
//import com.mike.ErrorResponse
//import com.mike.domain.model.meter.*
//import com.mike.service.meter.MeterService
//import com.mike.tuya.model.SetPriceRequest
//import com.mike.tuya.service.SmartMeterService
//import io.ktor.http.*
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.launch
//import java.time.LocalDateTime
//
//fun Route.deviceRoutes(
//    meterService: MeterService,
//    smartMeterService: SmartMeterService,
//
//    ) {
//
//    // Get all devices (respond with DB first, then update from cloud in background)
//    get("/devices") {
//        try {
//            // Respond immediately with local database devices
//            val dbDevices = meterService.getAllMeters()
//            call.respond(dbDevices)
//
//            // Launch background coroutine to fetch from cloud and update DB
//            coroutineScope {
//                launch {
//                    try {
//                        val connected = smartMeterService.connect()
//                        if (!connected) {
//                            return@launch
//                        }
//                        val cloudDevices = smartMeterService.listAllDevices()
//                        println("📱 (Background) Fetched ${cloudDevices.size} devices from Tuya Cloud")
//                        for (device in cloudDevices) {
//                            val existingMeter = meterService.findByDeviceId(device.id)
//                            if (existingMeter == null) {
//                                meterService.createMeter(
//                                    Meter(
//                                    deviceId = device.id,
//                                    name = device.name ?: "Smart Meter ${device.id}",
//                                    productName = device.productName,
//                                    description = "Auto-synced from Tuya Cloud",
//                                    location = null
//                                ))
//                            } else {
//                                meterService.updateMeter(
//                                    id = existingMeter.deviceId,
//                                    name = device.name ?: existingMeter.name,
//                                    productName = device.productName ?: existingMeter.productName,
//                                    description = existingMeter.description,
//                                    location = existingMeter.location,
//                                    active = existingMeter.active
//                                )
//                            }
//                        }
//                    } catch (e: Exception) {
//                        println("❌ (Background) Error syncing devices: ${e.message}")
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            println("❌ Error fetching devices: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("devices_error", e.message ?: "Failed to fetch devices")
//            )
//        }
//    }
//
//    // Get device by ID (available to all users)
//    get("/devices/{id}") {
//        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Try to find in database first
//            val device = meterService.findById(id)
//            if (device == null) {
//                // Not in database, try to get directly from Tuya Cloud
//                val connected = smartMeterService.connect()
//                if (!connected) {
//                    call.respond(
//                        HttpStatusCode.ServiceUnavailable,
//                        ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                    )
//                    return@get
//                }
//
//                // Fetch device details from Tuya Cloud
//                val deviceInfo = smartMeterService.getDeviceDetails(id)
//                if (deviceInfo != null) {
//                    // Cache the device in database
//                    meterService.createMeter(
//                        Meter(
//                        deviceId = deviceInfo.device.id,
//                        name = deviceInfo.device.name ?: "Smart Meter ${deviceInfo.device.id}",
//                        productName = deviceInfo.device.productName,
//                        description = "Auto-synced from Tuya Cloud",
//                        location = null,
//                        active  = true,
//                        createdAt = java.time.LocalDateTime.now(),
//                        updatedAt  = java.time.LocalDateTime.now()
//                    ))
//
//                    // Return full device info from cloud
//                    call.respond(deviceInfo)
//                } else {
//                    call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                }
//                return@get
//            }
//
//            // Get detailed device info from Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                // If cloud connection fails, just return the cached data
//                call.respond(device)
//                return@get
//            }
//
//            val deviceInfo = smartMeterService.getDeviceDetails(device.deviceId)
//            if (deviceInfo != null) {
//                call.respond(deviceInfo)
//            } else {
//                // If cloud data not available, return cached data
//                call.respond(device)
//            }
//
//        } catch (e: Exception) {
//            println("❌ Error fetching device: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("device_error", e.message ?: "Failed to fetch device")
//            )
//        }
//    }
//
////    // Register a new device (admin only)
////    post("/devices") {
////        // Authentication check removed - now available to all users
////        val request = call.receive<DeviceCreateRequest>()
////
////        try {
////            // Connect to Tuya Cloud
////            val connected = smartMeterService.connect()
////            if (!connected) {
////                call.respond(
////                    HttpStatusCode.ServiceUnavailable,
////                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
////                )
////                return@post
////            }
////
////            // Check if deviceId exists in Tuya
////            val devices = smartMeterService.listAllDevices()
////            val deviceExists = devices.any { it.id == request.deviceId }
////
////            if (!deviceExists) {
////                call.respond(
////                    HttpStatusCode.BadRequest,
////                    ErrorResponse("device_not_found", "Device ID not found in Tuya Cloud")
////                )
////                return@post
////            }
////
////            // Check if device already exists in database
////            val existingDevice = meterService.findByDeviceId(request.deviceId)
////            if (existingDevice != null) {
////                call.respond(
////                    HttpStatusCode.Conflict,
////                    ErrorResponse("device_exists", "Device with this ID already exists")
////                )
////                return@post
////            }
////
////            // Create device in database
////            val device = meterService.createMeter(
////                deviceId = request.deviceId,
////                name = request.name,
////                productName = request.productName,
////                description = request.description,
////                location = request.location
////            )
////
////            call.respond(HttpStatusCode.Created, device)
////
////        } catch (e: Exception) {
////            println("❌ Error creating device: ${e.message}")
////            e.printStackTrace()
////            call.respond(
////                HttpStatusCode.InternalServerError,
////                ErrorResponse("device_creation_error", e.message ?: "Failed to create device")
////            )
////        }
////    }
//
////    // Update device (admin only)
////    put("/devices/{id}") {
////        // Authentication check removed - now available to all users
////        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
////        val request = call.receive<DeviceUpdateRequest>()
////
////        try {
////            val updatedDevice = meterService.updateMeter(
////                id = id,
////                name = request.name,
////                productName = request.productName,
////                description = request.description,
////                location = request.location,
////                active = request.active
////            )
////
////            if (updatedDevice != null) {
////                call.respond(updatedDevice)
////            } else {
////                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
////            }
////
////        } catch (e: Exception) {
////            println("❌ Error updating device: ${e.message}")
////            e.printStackTrace()
////            call.respond(
////                HttpStatusCode.InternalServerError,
////                ErrorResponse("device_update_error", e.message ?: "Failed to update device")
////            )
////        }
////    }
//
////    // Delete device (admin only)
////    delete("/devices/{id}") {
////        // Authentication check removed - now available to all users
////        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
////
////        try {
////            val success = meterService.deleteMeter(id)
////
////            if (success) {
////                call.respond(HttpStatusCode.OK, mapOf("message" to "Device deleted"))
////            } else {
////                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
////            }
////
////        } catch (e: Exception) {
////            println("❌ Error deleting device: ${e.message}")
////            e.printStackTrace()
////            call.respond(
////                HttpStatusCode.InternalServerError,
////                ErrorResponse("device_deletion_error", e.message ?: "Failed to delete device")
////            )
////        }
////    }
//
//    // Add balance to device
//    post("/devices/{id}/add-balance") {
//        // Authentication check removed - now available to all users
//        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Check if device exists
//            val device = meterService.findById(id)
//            if (device == null) {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                return@post
//            }
//
//            val request = call.receive<AddBalanceRequest>()
//
//            // Connect to Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                call.respond(
//                    HttpStatusCode.ServiceUnavailable,
//                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                )
//                return@post
//            }
//
//            // Get current balance before adding money
//            val currentBalance = smartMeterService.getRemainingBalance(device.deviceId)
//            currentBalance.moneyBalance ?: 0.0
//
//            // Add balance to device
//            smartMeterService.addMoney(device.deviceId, request.amount)
//
////            if (result.success && true) {
////                // Record the successful payment in database
////                val payment = meterService.createDirectPayment(
////                    meterId = UUID.fromString(id),
////                    amount = BigDecimal(request.amount),
////                    description = "Direct balance top-up",
////                    balanceBefore = BigDecimal(balanceBefore.toString()),
////                    balanceAfter = result.additionalData?.get("balanceAfter")?.toString()?.toBigDecimalOrNull(),
////                    unitsAdded = BigDecimal(request.amount.toString())
////                )
////
////                // Include payment information in response
////                call.respond(mapOf(
////                    "success" to true,
////                    "message" to result.message,
////                    "payment" to payment
////                ))
////            } else {
////                call.respond(result)
////            }
//
//        } catch (e: Exception) {
//            println("❌ Error adding balance: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("command_error", e.message ?: "Failed to add balance")
//            )
//        }
//    }
//
//    // Send command to device
//    post("/devices/{id}/commands") {
//        // Authentication check removed - now available to all users
//        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Check if device exists
//            val device = meterService.findById(id)
//            if (device == null) {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                return@post
//            }
//
//            val request = call.receive<CustomCommandRequest>()
//
//            // Connect to Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                call.respond(
//                    HttpStatusCode.ServiceUnavailable,
//                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                )
//                return@post
//            }
//
//            val result = smartMeterService.sendCustomCommand(device.deviceId, request.code, request.value)
//            call.respond(result)
//
//        } catch (e: Exception) {
//            println("❌ Error sending command: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("command_error", e.message ?: "Failed to send command")
//            )
//        }
//    }
//
//    // Assign device to user (admin only)
//    post("/devices/{id}/assign") {
//        // Authentication check removed - now available to all users
//
//        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
//        val request = call.receive<DeviceUserAssignmentRequest>()
//
//        try {
//            val assignment = meterService.assignMeterToUser(request.userId, id)
//            call.respond(HttpStatusCode.Created, assignment)
//        } catch (e: IllegalArgumentException) {
//            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_input", e.message ?: "Invalid input"))
//        } catch (e: Exception) {
//            println("❌ Error assigning device: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("assignment_error", e.message ?: "Failed to assign device to user")
//            )
//        }
//    }
//
//    // Unassign device from user (admin only)
//    delete("/devices/{id}/assign/{userId}") {
//        // Authentication check removed - now available to all users
//
//        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
//        val userId = call.parameters["userId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            val success = meterService.removeMeterFromUser(userId, id)
//            if (success) {
//                call.respond(HttpStatusCode.OK, mapOf("message" to "Device unassigned from user"))
//            } else {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("assignment_not_found", "Assignment not found"))
//            }
//        } catch (e: Exception) {
//            println("❌ Error unassigning device: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("unassignment_error", e.message ?: "Failed to unassign device from user")
//            )
//        }
//    }
//
//    // Get users assigned to device (admin only)
//    get("/devices/{id}/users") {
//        // Authentication check removed - now available to all users
//
//        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            val users = meterService.getMeterUsers(id)
//            call.respond(users)
//        } catch (e: Exception) {
//            println("❌ Error getting assigned users: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("user_list_error", e.message ?: "Failed to get assigned users")
//            )
//        }
//    }
//
//    // Force refresh devices from cloud (admin only)
//    post("/devices/refresh") {
//        // Authentication check removed - now available to all users
//        try {
//            // Connect to Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                call.respond(
//                    HttpStatusCode.ServiceUnavailable,
//                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                )
//                return@post
//            }
//
//            // Fetch devices from Tuya Cloud
//            val cloudDevices = smartMeterService.listAllDevices()
//            println("📱 Fetched ${cloudDevices.size} devices from Tuya Cloud")
//
//            // Track new and updated devices
//            val newDevices = mutableListOf<String>()
//            val updatedDevices = mutableListOf<String>()
//
//            // Sync cloud devices with local database
//            for (device in cloudDevices) {
//                val existingMeter = meterService.findByDeviceId(device.id)
//                if (existingMeter == null) {
//                    // Create new device in database
//                    meterService.createMeter(
//                        deviceId = device.id,
//                        name = device.name ?: "Smart Meter ${device.id}",
//                        productName = device.productName,
//                        description = "Auto-synced from Tuya Cloud",
//                        location = null
//                    )
//                    newDevices.add(device.id)
//                    println("✅ Created new device for: ${device.id}")
//                } else {
//                    // Update existing device with cloud data
//                    meterService.updateMeter(
//                        id = existingMeter.id,
//                        name = device.name ?: existingMeter.name,
//                        productName = device.productName ?: existingMeter.productName,
//                        description = existingMeter.description,
//                        location = existingMeter.location,
//                        active = existingMeter.active
//                    )
//                    updatedDevices.add(device.id)
//                    println("🔄 Updated existing device: ${existingMeter.id}")
//                }
//            }
//
//            call.respond(
//                mapOf(
//                    "message" to "Device sync completed",
//                    "total" to cloudDevices.size,
//                    "new" to newDevices.size,
//                    "updated" to updatedDevices.size
//                )
//            )
//
//        } catch (e: Exception) {
//            println("❌ Error refreshing devices: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("refresh_error", e.message ?: "Failed to refresh devices")
//            )
//        }
//    }
//
//    // Process meter payment with MPesa
//    post("/devices/{id}/payment") {
//        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Check if device exists
//            val device = meterService.findById(id)
//            if (device == null) {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                return@post
//            }
//
//            val request = call.receive<MeterPaymentRequest>()
//
//            // Connect to Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                call.respond(
//                    HttpStatusCode.ServiceUnavailable,
//                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                )
//                return@post
//            }
//
//            // Get current balance
//            val currentBalance = smartMeterService.getRemainingBalance(device.deviceId)
//            currentBalance.moneyBalance ?: 0.0
//
//            // Process the payment based on payment type
//            when (request.paymentType) {
//                "MONEY" -> smartMeterService.addMoney(device.deviceId, request.amount)
//                "TOKEN" -> smartMeterService.addToken(
//                    device.deviceId, request.token
//                        ?: return@post call.respond(
//                            HttpStatusCode.BadRequest,
//                            ErrorResponse("token_required", "Token is required for TOKEN payment type")
//                        )
//                )
//
//                else -> return@post call.respond(
//                    HttpStatusCode.BadRequest,
//                    ErrorResponse("invalid_payment_type", "Payment type must be MONEY or TOKEN")
//                )
//            }
//
////            if (result.success) {
////                // Record the payment in database
////                val payment = if (request.mpesaTransactionId != null) {
////                    // Payment with MPesa
////                    val mpesaTransactionId = UUID.fromString(request.mpesaTransactionId)
////                    meterService.createPayment(
////                        userId = UUID.fromString(request.userId ?: call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString() ?: return@post call.respond(HttpStatusCode.BadRequest)),
////                        meterId = UUID.fromString(id),
////                        mpesaTransactionId = mpesaTransactionId,
////                        amount = BigDecimal(request.amount.toString()),
////                        description = request.description ?: "Payment via MPesa"
////                    )
////                } else {
////                    // Direct payment
////                    meterService.createDirectPayment(
////                        meterId = UUID.fromString(id),
////                        amount = BigDecimal(request.amount.toString()),
////                        description = request.description ?: "Direct payment",
////                        balanceBefore = BigDecimal(balanceBefore.toString()),
////                        balanceAfter = result.additionalData?.get("balanceAfter")?.toString()?.toBigDecimalOrNull(),
////                        unitsAdded = result.additionalData?.get("unitsAdded")?.toString()?.toBigDecimalOrNull()
////                    )
////                }
////
////                // Update payment status to reflect successful Tuya transaction
////                meterService.updatePaymentStatus(
////                    paymentId = payment.id,
////                    status = "COMPLETED",
////                    unitsAdded = result.additionalData?.get("unitsAdded")?.toString(),
////                    balanceBefore = BigDecimal(balanceBefore.toString()),
////                    balanceAfter = result.additionalData?.get("balanceAfter")?.toString()?.toBigDecimalOrNull()
////                )
////
////                call.respond(mapOf(
////                    "success" to true,
////                    "message" to result.message,
////                    "payment" to payment
////                ))
////            } else {
////                call.respond(mapOf(
////                    "success" to result.success,
////                    "message" to result.message,
////                    "result" to result
////                ))
////            }
//
//        } catch (e: Exception) {
//            println("❌ Error processing payment: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("payment_error", e.message ?: "Failed to process payment")
//            )
//        }
//    }
//
//    // Get meter payment history
//    get("/devices/{id}/payments") {
//        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Check if device exists
//            val device = meterService.findById(id)
//            if (device == null) {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                return@get
//            }
//
//            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
//
//            val payments = meterService.getPaymentsByMeterId(id).take(limit)
//
//            call.respond(
//                mapOf(
//                    "meterId" to id,
//                    "meterName" to device.name,
//                    "payments" to payments,
//                    "count" to (payments?.size ?: 0)
//                )
//            )
//
//        } catch (e: Exception) {
//            println("❌ Error fetching payment history: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("payment_history_error", e.message ?: "Failed to fetch payment history")
//            )
//        }
//    }
//
//    // Get meter billing information
//    get("/devices/{id}/billing") {
//        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Check if device exists
//            val device = meterService.findById(id)
//            if (device == null) {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                return@get
//            }
//
//            // Connect to Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                call.respond(
//                    HttpStatusCode.ServiceUnavailable,
//                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                )
//                return@get
//            }
//
//            val balance = smartMeterService.getRemainingBalance(device.deviceId)
//            val usage = smartMeterService.getEnergyUsage(device.deviceId)
//
//            // Get last payment if repository is available
//            val lastPayment = meterService.getPaymentsByMeterId(id).firstOrNull()
//
//            call.respond(
//                mapOf(
//                    "meterId" to id,
//                    "meterName" to device.name,
//                    "deviceId" to device.deviceId,
//                    "balance" to balance,
//                    "usage" to usage,
//                    "lastPayment" to lastPayment
//                )
//            )
//
//        } catch (e: Exception) {
//            println("❌ Error fetching billing information: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("billing_info_error", e.message ?: "Failed to fetch billing information")
//            )
//        }
//    }
//
//    // Set meter rate (price per unit)
//    post("/devices/{id}/set-rate") {
//        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
//
//        try {
//            // Check if device exists
//            val device = meterService.findById(id)
//            if (device == null) {
//                call.respond(HttpStatusCode.NotFound, ErrorResponse("device_not_found", "Device not found"))
//                return@post
//            }
//
//            val request = call.receive<SetPriceRequest>()
//
//            // Connect to Tuya Cloud
//            val connected = smartMeterService.connect()
//            if (!connected) {
//                call.respond(
//                    HttpStatusCode.ServiceUnavailable,
//                    ErrorResponse("connection_error", "Failed to connect to Tuya Cloud")
//                )
//                return@post
//            }
//
//            val result = smartMeterService.setUnitPrice(
//                deviceId = device.deviceId,
//                price = request.price,
//                currencySymbol = request.currencySymbol
//            )
//
//            call.respond(result)
//
//        } catch (e: Exception) {
//            println("❌ Error setting meter rate: ${e.message}")
//            e.printStackTrace()
//            call.respond(
//                HttpStatusCode.InternalServerError,
//                ErrorResponse("rate_setting_error", e.message ?: "Failed to set meter rate")
//            )
//        }
//    }
//}
//
//
//data class MeterPaymentRequest(
//    val paymentType: String, // "MONEY" or "TOKEN"
//    val amount: Double,
//    val userId: String? = null,
//    val description: String? = null,
//    val token: String? = null,
//    val mpesaTransactionId: String? = null
//)
//
//data class AddBalanceRequest(
//    val amount: Double
//)
//
//data class CustomCommandRequest(
//    val code: String, // Command code to send
//    val value: Any // Value for the command
//)