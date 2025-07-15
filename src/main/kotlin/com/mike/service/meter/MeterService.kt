package com.mike.service.meter

import com.mike.domain.repository.meter.MeterRepository
import com.mike.domain.model.meter.MeterDto
import com.mike.domain.repository.meter.MeterPaymentRepository
import com.mike.domain.model.meter.MeterPaymentDto
import com.mike.domain.model.user.UserDto
import com.mike.domain.model.user.UserMeterAssignmentDto
import com.mike.domain.repository.user.UserMeterAssignmentRepository

class MeterService(
    private val meterRepository: MeterRepository,
    private val meterPaymentRepository: MeterPaymentRepository,
    private val userMeterAssignmentRepository: UserMeterAssignmentRepository

) {
    fun findByDeviceId(deviceId: String): MeterDto? =
        meterRepository.findByDeviceId(deviceId)

    fun findById(id: String): MeterDto? =
        meterRepository.findById(id)

    fun getAllMeters(): List<MeterDto> =
        meterRepository.getAllMeters()

    fun createMeter(
        deviceId: String,
        name: String,
        productName: String? = null,
        description: String? = null,
        location: String? = null
    ): MeterDto =
        meterRepository.createMeter(deviceId, name, productName, description, location)

    fun updateMeter(
        id: String,
        name: String? = null,
        productName: String? = null,
        description: String? = null,
        location: String? = null,
        active: Boolean? = null
    ): MeterDto? =
        meterRepository.updateMeter(id, name, productName, description, location, active)

    fun deleteMeter(id: String): Boolean =
        meterRepository.deleteMeter(id)

    // MeterPaymentRepository functions
    suspend fun createPayment(
        userId: String,
        meterId: String,
        mpesaTransactionId: String,
        amount: Double,
        description: String? = null
    ): MeterPaymentDto =
        meterPaymentRepository.createPayment(userId, meterId, mpesaTransactionId, amount, description)

    fun assignMeterToUser(userId: String, meterId: String): UserMeterAssignmentDto =
        userMeterAssignmentRepository.assignMeterToUser(userId, meterId)

    fun removeMeterFromUser(userId: String, meterId: String): Boolean =
        userMeterAssignmentRepository.removeMeterFromUser(userId, meterId)

    fun getUserMeters(userId: String): List<MeterDto> =
        userMeterAssignmentRepository.getUserMeters(userId)

    fun getMeterUsers(meterId: String): List<UserDto> =
        userMeterAssignmentRepository.getMeterUsers(meterId)

    fun isMeterAssignedToUser(userId: String, meterId: String): Boolean =
        userMeterAssignmentRepository.isMeterAssignedToUser(userId, meterId)

    fun getAllAssignments(): List<UserMeterAssignmentDto> =
        userMeterAssignmentRepository.getAllAssignments()

    suspend fun updatePaymentStatus(
        paymentId: String,
        status: String,
        unitsAdded: Double? = null,
        balanceBefore: Double? = null,
        balanceAfter: Double? = null
    ): MeterPaymentDto? =
        meterPaymentRepository.updatePaymentStatus(paymentId, status, unitsAdded, balanceBefore, balanceAfter)

    suspend fun getPaymentById(id: String): MeterPaymentDto? =
        meterPaymentRepository.getPaymentById(id)

    suspend fun getPaymentsByUserId(userId: String): List<MeterPaymentDto> =
        meterPaymentRepository.getPaymentsByUserId(userId)

    suspend fun getPaymentsByMeterId(meterId: String): List<MeterPaymentDto> =
        meterPaymentRepository.getPaymentsByMeterId(meterId)

    suspend fun getPaymentsByStatus(status: String): List<MeterPaymentDto> =
        meterPaymentRepository.getPaymentsByStatus(status)

    suspend fun getPaymentsByMpesaTransactionId(mpesaTransactionId: String): MeterPaymentDto? =
        meterPaymentRepository.getPaymentsByMpesaTransactionId(mpesaTransactionId)

    suspend fun getAllPayments(): List<MeterPaymentDto> =
        meterPaymentRepository.getAllPayments()

    suspend fun getUserPaymentHistory(userId: String, limit: Int = 50): List<MeterPaymentDto> =
        meterPaymentRepository.getUserPaymentHistory(userId, limit)

    suspend fun createDirectPayment(
        meterId: String,
        amount: Double,
        description: String? = null,
        balanceBefore: Double? = null,
        balanceAfter: Double? = null,
        unitsAdded: Double? = null
    ): MeterPaymentDto =
        meterPaymentRepository.createDirectPayment(meterId, amount, description, balanceBefore, balanceAfter, unitsAdded)
}
