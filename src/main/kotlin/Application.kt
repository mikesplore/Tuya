package com.mike

import com.mike.database.DatabaseFactory
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init(environment.config)
    
    configureSerialization()
    configureSecurity()
    configureRouting()
}
