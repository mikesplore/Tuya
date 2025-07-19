package com.mike.tuya

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterPayment
import java.math.BigDecimal

interface TuyaRepository {
    suspend fun fetchOnlineDevices(): List<Meter>

    /**
     * Charge a meter with a specified amount of energy units
     *
     * @param meterId The ID of the meter to charge
     * @param amount The amount of units to add
     * @param userId Optional user ID for tracking who made the charge
     * @return true if the charge was successful, false otherwise
     */
    suspend fun chargeMeter(meterId: String, amount: Int, userId: Int? = null): Boolean

    /**
     * Set the price/rate per unit for a meter
     *
     * @param meterId The ID of the meter to update
     * @param price The new price per unit
     * @return true if the rate was updated successfully, false otherwise
     */
    suspend fun setMeterRate(meterId: String, price: Int): Boolean

    /**
     * Get the current balance of a meter
     *
     * @param meterId The ID of the meter to query
     * @return The current balance in units, or null if not available
     */
    suspend fun getMeterBalance(meterId: String): Int?

    /**
     * Get the current rate/price per unit of a meter
     *
     * @param meterId The ID of the meter to query
     * @return The current price per unit, or null if not available
     */
    suspend fun getMeterRate(meterId: String): Int?

    /**
     * Toggle the prepayment mode of a meter
     *
     * @param meterId The ID of the meter to update
     * @param enabled Whether prepayment mode should be enabled
     * @return true if the mode was updated successfully, false otherwise
     */
    suspend fun togglePrepaymentMode(meterId: String, enabled: Boolean): Boolean

    /**
     * Get the payment/charging history for a meter
     *
     * @param meterId The ID of the meter to query
     * @return A list of payment records for the meter
     */
    suspend fun getMeterHistory(meterId: String): List<MeterPayment>
}
