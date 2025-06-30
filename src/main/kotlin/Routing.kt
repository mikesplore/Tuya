package com.mike

import com.mike.database.repository.MeterRepository
import com.mike.database.repository.UserMeterAssignmentRepository
import com.mike.database.repository.UserRepository
import com.mike.routes.deviceRoutes
import com.mike.routes.userRoutes
import com.mike.tuya.config.getTuyaConfig
import com.mike.tuya.service.SmartMeterService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
    
    // Initialize repositories
    val userRepository = UserRepository()
    val meterRepository = MeterRepository()
    val userMeterAssignmentRepository = UserMeterAssignmentRepository()
    
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
        
        // Device management routes (unprotected)
        deviceRoutes(meterRepository, userMeterAssignmentRepository, smartMeterService)
        
        // Authenticated routes
        authenticate {
            // User management routes
            userRoutes(userRepository)
        }
    }
}
