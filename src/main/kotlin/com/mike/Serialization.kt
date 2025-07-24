package com.mike

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        gson {
            // Configure Gson with pretty printing and lenient parsing
            serializeNulls()
            disableHtmlEscaping()
            registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
                private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                override fun write(out: JsonWriter, value: LocalDateTime?) {
                    if (value == null) {
                        out.nullValue()
                    } else {
                        out.value(value.format(formatter))
                    }
                }
                override fun read(`in`: JsonReader): LocalDateTime? {
                    return if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
                        `in`.nextNull()
                        null
                    } else {
                        LocalDateTime.parse(`in`.nextString(), formatter)
                    }
                }
            })
        }
    }
    
    routing {
        get("/json/gson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}
