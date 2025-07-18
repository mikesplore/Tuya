package com.mike.tuya

import com.mike.domain.model.meter.Meter
import org.slf4j.LoggerFactory

interface TuyaService {
    suspend fun getOnlineDevices(): List<Meter>
}

class TuyaServiceImpl(private val repository: TuyaRepository) : TuyaService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun getOnlineDevices(): List<Meter> {
        logger.info("Fetching online devices from Tuya API")
        return try {
            repository.fetchOnlineDevices()
        } catch (e: Exception) {
            logger.error("Error fetching online devices", e)
            emptyList()
        }
    }
}
