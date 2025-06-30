package com.mike

import com.mike.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
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

    configureSerialization()
    configureSecurity()
    configureRouting()
}
