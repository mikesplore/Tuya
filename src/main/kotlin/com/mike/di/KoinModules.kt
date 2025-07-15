package com.mike.di

import com.mike.domain.repository.meter.MeterPaymentRepository
import com.mike.domain.repository.meter.MeterPaymentRepositoryImpl
import com.mike.domain.repository.meter.MeterRepository
import com.mike.domain.repository.meter.MeterRepositoryImpl
import com.mike.domain.repository.mpesa.MpesaRepository
import com.mike.domain.repository.mpesa.MpesaRepositoryImpl
import com.mike.domain.repository.user.UserMeterAssignmentRepository
import com.mike.domain.repository.user.UserMeterAssignmentRepositoryImpl
import org.koin.dsl.module
import com.mike.domain.repository.user.UserRepository
import com.mike.domain.repository.user.UserRepositoryImpl
import com.mike.service.meter.MeterService
import com.mike.service.mpesa.MpesaService
import com.mike.service.user.UserService
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.gson.gson


// Main module that combines all other modules
val appModule = module {

    val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            gson()
        }
    }

    single<UserRepository> { UserRepositoryImpl() }

    single<MeterPaymentRepository> { MeterPaymentRepositoryImpl() }

    single<MpesaRepository> { MpesaRepositoryImpl(httpClient, get()) }

    single<MeterRepository> { MeterRepositoryImpl() }

    single { UserService(get(), get()) }

    single { MpesaService(get(), get()) }

    single { UserService(get(), get()) }

    single { MeterService(get(), get(), get()) }

    single<UserMeterAssignmentRepository> { UserMeterAssignmentRepositoryImpl() }

    }

