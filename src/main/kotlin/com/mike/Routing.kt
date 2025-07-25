package com.mike

import com.mike.auth.JwtService
import com.mike.routes.authRoutes
import com.mike.routes.meterRoutes
import com.mike.routes.meterUserRoutes
import com.mike.routes.mpesaCallbackRoute
import com.mike.routes.mpesaRoutes
import com.mike.routes.statsRoutes
import com.mike.routes.tuyaRoutes
import com.mike.service.auth.AuthService
import com.mike.routes.userRoutes
import com.mike.service.meter.MeterService
import com.mike.service.meter.MeterUserService
import com.mike.service.mpesa.MpesaService
import com.mike.service.stats.StatsService
import com.mike.service.user.UserService
import com.mike.service.tuya.TuyaService
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
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mike.service.meter.MeterPaymentProcessingService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

data class ErrorResponse(
    val error: String,
    val message: String
)

fun Application.configureRouting(
    userService: UserService,
    authService: AuthService,
    meterService: MeterService,
    meterUserService: MeterUserService,
    tuyaService: TuyaService,
    mpesaService: MpesaService,
    jwtService: JwtService,
    meterPaymentProcessingService: MeterPaymentProcessingService,
    statsService: StatsService
) {
    // Install CORS
    install(CORS) {
        anyHost()
        allowCredentials = true
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


    // Initialize HTTP client
    HttpClient(Apache) {
        install(ContentNegotiation) {
            gson()
        }
    }


    routing {
        // Root route
        get("/") {
            call.respondText("Tuya Smart Meter API - Ktor Backend")
        }
        // Public routes
        authRoutes(authService)
    }

    routing {
        authenticate("auth-jwt") {
            userRoutes(userService, jwtService)
            meterRoutes(meterService, jwtService, userService)
            meterUserRoutes(meterUserService)
            tuyaRoutes(tuyaService)
            mpesaRoutes(mpesaService, meterPaymentProcessingService, jwtService, userService)
            statsRoutes(jwtService, statsService, userService)
        }
    }

    routing {
        // Add the M-Pesa callback route outside of authentication
        mpesaCallbackRoute(mpesaService, meterPaymentProcessingService)
    }
}
