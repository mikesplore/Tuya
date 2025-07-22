package com.mike

import com.mike.apidocumentation.configureSwagger
import com.mike.auth.JwtService
import com.mike.database.DatabaseFactory
import com.mike.di.appModule
import com.mike.domain.repository.mpesa.MpesaRepository
import com.mike.service.auth.AuthService
import com.mike.service.meter.MeterService
import com.mike.service.meter.MeterUserService
import com.mike.service.mpesa.MpesaService
import com.mike.service.tuya.TuyaService
import com.mike.service.user.UserService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import kotlinx.coroutines.*
import org.koin.core.context.startKoin
import org.koin.ktor.ext.get
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level
import java.time.Duration

fun main(args: Array<String>) {
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

    // Environment variables
    dotenv {
        ignoreIfMissing = true
        directory = System.getProperty("user.dir")
    }

    // Services and repositories
    val jwtService = get<JwtService>()
    val userService = get<UserService>()
    val authService = get<AuthService>()
    val meterService = get<MeterService>()
    val meterUserService = get<MeterUserService>()
    val tuyaService = get<TuyaService>()
    val mpesaService = get<MpesaService>()
    val mpesaRepository = get<MpesaRepository>()
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
        jwtService
    )

    // Start background job for Mpesa pending transactions
    startMpesaPendingTransactionMonitor(mpesaRepository)
}

@OptIn(DelicateCoroutinesApi::class)
fun startMpesaPendingTransactionMonitor(mpesaRepository: MpesaRepository) {
    GlobalScope.launch {
        while (true) {
            try {
                val pendingTransactions = mpesaRepository.getTransactionsByStatus("PENDING")
                if (pendingTransactions.isNotEmpty()) {
                    println("Found ${pendingTransactions.size} pending M-Pesa transactions. Querying status...")
                    for (transaction in pendingTransactions) {
                        transaction.checkoutRequestId?.let { checkoutId ->
                            runCatching {
                                runBlocking { mpesaRepository.queryTransactionStatus(checkoutId) }
                            }.onFailure {
                                println("Failed to query status for $checkoutId: ${it.message}")
                            }
                        }
                    }
                } else {
                    println("No pending M-Pesa transactions found.")
                }
            } catch (e: Exception) {
                println("Error in M-Pesa transaction monitor: ${e.message}")
            }
            delay(Duration.ofMinutes(5).toMillis())
        }
    }
}