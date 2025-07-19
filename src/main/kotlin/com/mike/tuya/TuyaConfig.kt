package com.mike.tuya

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory

class TuyaConfig {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val accessId: String = dotenv["ACCESS_ID"] ?: ""
    val accessSecret: String = dotenv["ACCESS_SECRET"] ?: ""
    val tuyaEndpoint: String = dotenv["TUYA_ENDPOINT"] ?: "https://openapi.tuyaeu.com"
    val projectCode: String? = dotenv["PROJECT_CODE"]

    init {
        if (accessId.isBlank() || accessSecret.isBlank()) {
            logger.warn("Empty credentials detected in TuyaConfig!")
        }
    }
}
