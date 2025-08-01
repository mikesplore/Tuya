package com.mike

import com.mike.apidocumentation.configureSwagger
import com.mike.auth.JwtService
import com.mike.database.DatabaseFactory
import com.mike.di.appModule
import com.mike.service.auth.AuthService
import com.mike.service.meter.MeterPaymentProcessingService
import com.mike.service.meter.MeterService
import com.mike.service.meter.MeterUserService
import com.mike.service.mpesa.MpesaService
import com.mike.service.stats.StatsService
import com.mike.service.tuya.TuyaService
import com.mike.service.user.UserService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import org.koin.core.context.startKoin
import org.koin.ktor.ext.get
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

fun main(args: Array<String>) {
    // Load .env before starting Ktor
    val env = dotenv {
        ignoreIfMissing = true
        directory = System.getProperty("user.dir")
    }
    env.entries().forEach { entry ->
        System.setProperty(entry.key, entry.value)
    }
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init(environment.config)

    // Logging
    install(CallLogging) {
        level = Level.INFO
    }

    // Dependency Injection
    startKoin {
        slf4jLogger()
        properties(mapOf("applicationConfig" to environment.config))
        modules(appModule)
    }

    // Services and repositories
    val jwtService = get<JwtService>()
    val userService = get<UserService>()
    val authService = get<AuthService>()
    val meterService = get<MeterService>()
    val meterUserService = get<MeterUserService>()
    val tuyaService = get<TuyaService>()
    val mpesaService = get<MpesaService>()
    val meterPaymentProcessingService = get<MeterPaymentProcessingService>()
    val statsService = get<StatsService>()
    val appConfig = environment.config
    // Authentication
    install(Authentication) {
        jwtService.configureJwtAuthentication(appConfig, this)
    }

    // Serialization, Swagger, Routing
    configureSerialization()
    configureSwagger()
    configureRouting(
        userService,
        authService,
        meterService,
        meterUserService,
        tuyaService,
        mpesaService,
        jwtService,
        meterPaymentProcessingService,
        statsService
    )

    // Start a background job for Mpesa pending transactions
    mpesaService.startMpesaPendingTransactionMonitor()
}
