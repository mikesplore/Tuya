//package com.mike.service.meter
//
//import com.mike.domain.model.meter.Meter
//import com.mike.domain.model.meter.MeterPayment
//import com.mike.domain.model.user.Profile
//import com.mike.domain.model.user.UserMeterAssignment
//import com.mike.domain.repository.meter.MeterRepository
//import com.mike.domain.repository.meter.MeterPaymentRepository
//import com.mike.domain.repository.user.UserMeterAssignmentRepository
//
//class MeterService(
//    private val meterRepository: MeterRepository,
//    private val meterPaymentRepository: MeterPaymentRepository,
//    private val userMeterAssignmentRepository: UserMeterAssignmentRepository
//
//) {
//    fun findByDeviceId(deviceId: String): Meter? =
//        meterRepository.findByDeviceId(deviceId)
//
//    fun findById(id: String): Meter? =
//        meterRepository.findById(id)
//
//    fun getAllMeters(): List<Meter> =
//        meterRepository.getAllMeters()
//
//    fun createMeter(meter: Meter) =
//        meterRepository.createMeter(meter)
//
//    fun updateMeter(meter: Meter) =
//        meterRepository.updateMeter(meter)
//
//    fun deleteMeter(id: String): Boolean =
//        meterRepository.deleteMeter(id)
//
//    // MeterPaymentRepository functions
//    suspend fun createPayment(meterPayment: MeterPayment) =
//        meterPaymentRepository.createPayment(meterPayment)
//
//    fun assignMeterToUser(userId: Int, meterId: String): UserMeterAssignment =
//        userMeterAssignmentRepository.assignMeterToUser(userId, meterId)
//
//    fun removeMeterFromUser(userId: Int, meterId: String): Boolean =
//        userMeterAssignmentRepository.removeMeterFromUser(userId, meterId)
//
//    fun getUserMeters(userId: Int): List<Meter> =
//        userMeterAssignmentRepository.getUserMeters(userId)
//
//    fun getMeterUsers(meterId: String): List<Profile> =
//        userMeterAssignmentRepository.getMeterUsers(meterId)
//
//    fun isMeterAssignedToUser(userId: Int, meterId: String): Boolean =
//        userMeterAssignmentRepository.isMeterAssignedToUser(userId, meterId)
//
//    fun getAllAssignments(): List<UserMeterAssignment> =
//        userMeterAssignmentRepository.getAllAssignments()
//
//    suspend fun updatePaymentStatus(meterPayment: MeterPayment) =
//        meterPaymentRepository.updatePaymentStatus(meterPayment)
//
//    suspend fun getPaymentById(id: Int): MeterPayment? =
//        meterPaymentRepository.getPaymentById(id)
//
//    suspend fun getPaymentsByUserId(userId: Int): List<MeterPayment> =
//        meterPaymentRepository.getPaymentsByUserId(userId)
//
//    suspend fun getPaymentsByMeterId(meterId: String): List<MeterPayment> =
//        meterPaymentRepository.getPaymentsByMeterId(meterId)
//
//    suspend fun getPaymentsByStatus(status: String): List<MeterPayment> =
//        meterPaymentRepository.getPaymentsByStatus(status)
//
//    suspend fun getPaymentsByMpesaTransactionId(mpesaTransactionId: String): MeterPayment? =
//        meterPaymentRepository.getPaymentsByMpesaTransactionId(mpesaTransactionId)
//
//    suspend fun getAllPayments(): List<MeterPayment> =
//        meterPaymentRepository.getAllPayments()
//
//    suspend fun getUserPaymentHistory(userId: Int, limit: Int = 50): List<MeterPayment> =
//        meterPaymentRepository.getUserPaymentHistory(userId, limit)
//
//
//}
