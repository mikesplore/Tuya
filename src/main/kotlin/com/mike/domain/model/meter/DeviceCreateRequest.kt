package com.mike.domain.model.meter

data class DeviceCreateRequest(
    val deviceId: String,
    val name: String,
    val productName: String?,
    val description: String?,
    val location: String?
)
