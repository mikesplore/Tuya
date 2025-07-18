package com.mike

//import com.mike.routes.deviceRoutes
//import com.mike.routes.mpesaRoutes
import com.mike.routes.authRoutes
import com.mike.routes.meterRoutes
import com.mike.routes.meterUserRoutes
import com.mike.service.auth.AuthService
import com.mike.routes.userRoutes
import com.mike.service.meter.MeterService
import com.mike.service.meter.MeterUserService
//import com.mike.service.meter.MeterService
//import com.mike.service.mpesa.MpesaService
import com.mike.service.user.UserService
import com.mike.tuya.config.getTuyaConfig
import com.mike.tuya.service.SmartMeterService
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

data class ErrorResponse(
    val error: String,
    val message: String
)

fun Application.configureRouting(userService: UserService, authService: AuthService, meterService: MeterService, meterUserService: MeterUserService) {
    // Install CORS
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

    // Install Status Pages
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", cause.message ?: "Unknown error")
            )
        }
    }

    // Initialize configs
    val tuyaConfig = getTuyaConfig()

    // Initialize services
    val smartMeterService = SmartMeterService(
        accessId = tuyaConfig.accessId,
        accessSecret = tuyaConfig.accessSecret,
        endpoint = tuyaConfig.endpoint,
        projectCode = tuyaConfig.projectCode
    )

    // Initialize HTTP client
    HttpClient(Apache) {
        install(ContentNegotiation) {
            gson()
        }
    }

    // Store SmartMeterService in application attributes for callback access
    attributes.put(AttributeKey<SmartMeterService>("SmartMeterService"), smartMeterService)

    routing {
        // Root route
        get("/") {
            call.respondText("Tuya Smart Meter API - Ktor Backend")
        }

        // Health check route
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
//        deviceRoutes(
//            meterService,
//            smartMeterService,
//        )
//
//        // Mpesa routes
//        mpesaRoutes(
//            mpesaService,
//            userService,
//            meterService
//        )

        // User management routes (protected)
        userRoutes(userService)
        authRoutes(authService)
        meterRoutes(meterService)
        meterUserRoutes(meterUserService)
    }
}
