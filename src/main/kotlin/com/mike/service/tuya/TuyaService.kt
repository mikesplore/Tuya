package com.mike.service.tuya

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterPayment
import com.mike.tuya.TuyaRepository
import org.slf4j.LoggerFactory

class TuyaService(
    private val repository: TuyaRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getOnlineDevices(): List<Meter> {
        logger.info("Fetching online devices from Tuya API")
        return try {
            repository.fetchOnlineDevices()
        } catch (e: Exception) {
            logger.error("Error fetching online devices", e)
            emptyList()
        }
    }

    /**
     * Charge a meter with the specified amount of energy units
     *
     * @param meterId ID of the meter to charge
     * @param amount Amount of energy units to add
     * @param userId Optional user ID to track who made the charge
     * @return true if successful, false otherwise
     */
    suspend fun chargeMeter(meterId: String, amount: Int, userId: Int? = null): Boolean {
        logger.info("Charging meter $meterId with $amount units")
        return try {
            repository.chargeMeter(meterId, amount, userId)
        } catch (e: Exception) {
            logger.error("Error charging meter: ${e.message}", e)
            false
        }
    }

    /**
     * Set the price/rate per unit for a meter
     *
     * @param meterId ID of the meter to update
     * @param price The new price per unit
     * @return true if successful, false otherwise
     */
    suspend fun setMeterRate(meterId: String, price: Int): Boolean {
        logger.info("Setting meter $meterId rate to $price")
        return try {
            repository.setMeterRate(meterId, price)
        } catch (e: Exception) {
            logger.error("Error setting meter rate: ${e.message}", e)
            false
        }
    }

    /**
     * Get the current balance of a meter
     *
     * @param meterId ID of the meter to query
     * @return The balance in units, or null if unavailable
     */
    suspend fun getMeterBalance(meterId: String): Int? {
        logger.info("Getting balance for meter $meterId")
        return try {
            repository.getMeterBalance(meterId)
        } catch (e: Exception) {
            logger.error("Error getting meter balance: ${e.message}", e)
            null
        }
    }

    /**
     * Get the current price/rate per unit for a meter
     *
     * @param meterId ID of the meter to query
     * @return The price per unit, or null if unavailable
     */
    suspend fun getMeterRate(meterId: String): Int? {
        logger.info("Getting rate for meter $meterId")
        return try {
            repository.getMeterRate(meterId)
        } catch (e: Exception) {
            logger.error("Error getting meter rate: ${e.message}", e)
            null
        }
    }

    /**
     * Toggle prepayment mode for a meter
     *
     * @param meterId ID of the meter to update
     * @param enabled Whether prepayment mode should be on or off
     * @return true if successful, false otherwise
     */
    suspend fun togglePrepaymentMode(meterId: String, enabled: Boolean): Boolean {
        logger.info("Setting meter $meterId prepayment mode to $enabled")
        return try {
            repository.togglePrepaymentMode(meterId, enabled)
        } catch (e: Exception) {
            logger.error("Error toggling prepayment mode: ${e.message}", e)
            false
        }
    }

    suspend fun clearBalance(meterId: String): Boolean {
        logger.info("Clearing balance for meter $meterId")
        return try {
            repository.clearBalance(meterId)
        } catch (e: Exception) {
            logger.error("Error clearing meter balance: ${e.message}", e)
            false
        }
    }

    /**
     * Get payment history for a meter
     *
     * @param meterId ID of the meter to query
     * @return List of payment records, or empty list if none found
     */
    suspend fun getMeterHistory(meterId: String): List<MeterPayment> {
        logger.info("Getting payment history for meter $meterId")
        return try {
            repository.getMeterHistory(meterId)
        } catch (e: Exception) {
            logger.error("Error getting meter history: ${e.message}", e)
            emptyList()
        }
    }
}
