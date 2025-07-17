package com.mike.domain.model.meter

data class DeviceUpdateRequest(
    val name: String,
    val productName: String?,
    val description: String?,
    val location: String?,
    val active: Boolean
)
