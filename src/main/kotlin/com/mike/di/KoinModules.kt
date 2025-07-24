package com.mike.di

import com.mike.auth.JwtService
import com.mike.domain.repository.auth.AuthRepository
import com.mike.domain.repository.auth.AuthRepositoryImpl
import com.mike.domain.repository.meter.MeterPaymentRepository
import com.mike.domain.repository.meter.MeterPaymentRepositoryImpl
import com.mike.domain.repository.meter.MeterRateRepository
import com.mike.domain.repository.meter.MeterRateRepositoryImpl
import com.mike.domain.repository.meter.MeterRepository
import com.mike.domain.repository.meter.MeterRepositoryImpl
import com.mike.domain.repository.meter.MeterUserAssignmentRepository
import com.mike.domain.repository.meter.MeterUserAssignmentRepositoryImpl
import com.mike.domain.repository.mpesa.MpesaRepository
import com.mike.domain.repository.mpesa.MpesaRepositoryImpl
import org.koin.dsl.module
import com.mike.domain.repository.user.UserRepository
import com.mike.domain.repository.user.UserRepositoryImpl
import com.mike.service.auth.AuthService
import com.mike.service.meter.MeterPaymentProcessingService
import com.mike.service.meter.MeterService
import com.mike.service.meter.MeterUserService
import com.mike.service.mpesa.MpesaService
import com.mike.service.user.UserService
import com.mike.tuya.TuyaAuth
import com.mike.tuya.TuyaConfig
import com.mike.tuya.TuyaRepository
import com.mike.tuya.TuyaRepositoryImpl
import com.mike.service.tuya.TuyaService
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.gson.gson
import io.ktor.server.config.ApplicationConfig


// Main module that combines all other modules
val appModule = module {

    val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            gson()
        }
    }
    val tuyaConfig = TuyaConfig()
    val tuyaAuth = TuyaAuth(tuyaConfig, httpClient)

    single { getProperty<ApplicationConfig>("applicationConfig") }
    single { JwtService(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get()) }

    single<TuyaRepository> { TuyaRepositoryImpl(httpClient, tuyaConfig, tuyaAuth) }
    single<MeterRepository> { MeterRepositoryImpl(get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<MeterUserAssignmentRepository> { MeterUserAssignmentRepositoryImpl(get(), get()) }
    single<MeterRateRepository> { MeterRateRepositoryImpl() }


    single { AuthService(get(), get()) }

    single<MeterPaymentRepository> { MeterPaymentRepositoryImpl(get()) }

    single<MpesaRepository> { MpesaRepositoryImpl(httpClient, get()) }

    single { UserService(get()) }

    single { MpesaService(get()) }

    single { MeterService(get()) }
    single { TuyaService(get()) }
    single { MeterUserService(get()) }
    single { MeterPaymentProcessingService(get(), get(), get()) }

    }

