package com.mike.tuya

import com.mike.domain.model.meter.Meter

interface TuyaRepository {
    suspend fun fetchOnlineDevices(): List<Meter>
}
