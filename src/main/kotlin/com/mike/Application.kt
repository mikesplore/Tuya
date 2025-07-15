package com.mike

import com.mike.database.DatabaseFactory
import com.mike.di.appModule
import com.mike.domain.repository.user.UserRepository
import com.mike.service.meter.MeterService
import com.mike.service.mpesa.MpesaService
import com.mike.service.user.UserService
import com.mike.tuya.service.SmartMeterService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import org.koin.core.context.startKoin
import org.koin.ktor.ext.get
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init(environment.config)
    
    // Add call logging
    install(CallLogging) {
        level = Level.INFO
    }

    startKoin {
        slf4jLogger()
        modules(appModule)
    }

    val dotenv = dotenv {
        ignoreIfMissing = true
        directory = System.getProperty("user.dir")
    }

    val accessId = dotenv["ACCESS_ID"]

    val accessSecret = dotenv["ACCESS_SECRET"]

    val endpoint = dotenv["TUYA_ENDPOINT"]

    val userService = get<UserService>()
    val meterService = get<MeterService>()
    val mpesaService = get<MpesaService>()
    val userRepository = get<UserRepository>()
    val smartMeterService = SmartMeterService(accessId, accessSecret, endpoint = endpoint)

    configureSerialization()
    configureSecurity(userRepository)
    configureRouting(mpesaService, userService, meterService, smartMeterService)
}
