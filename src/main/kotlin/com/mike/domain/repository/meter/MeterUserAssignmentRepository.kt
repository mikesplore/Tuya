package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.domain.model.user.Profile


interface MeterUserAssignmentRepository {
     /**
      * Assigns a meter to a user. If the meter is already assigned to another user,
      * it will be unassigned first.
      *
      * @param meterUserAssignment the meter-user assignment data
      * @return true if the assignment was successful, false otherwise
      */
     fun assignMeterToUser(meterUserAssignment: MeterUserAssignment): Boolean

     /**
      * Unassigns a meter from a user by removing the assignment record.
      *
      * @param meterUserAssignment the meter-user assignment data
      * @return true if the unassignment was successful, false if no such assignment existed
      */
     fun unassignMeterFromUser(meterUserAssignment: MeterUserAssignment): Boolean

     /**
      * Retrieves a list of meters assigned to a specific user.
      *
      * @param userId The unique identifier of the user
      * @return A list of meters assigned to the specified user
      */
     fun getAssignedMetersByUser(userId: Int): List<Meter>

     /**
      * Retrieves a list of meters that are not assigned to any user.
      *
      * @return A list of meters that are currently not assigned to any user
      */
     fun getUnassignedMeters(): List<Meter>

     /**
      * Retrieves a list of user profiles associated with a specific meter.
      * Since a meter can only be assigned to one user, this will return
      * either a list with one element or an empty list.
      *
      * @param meterId The unique identifier of the meter
      * @return A list containing the user profile associated with the meter, or an empty list
      */
     fun getUsersByMeter(meterId: String): List<Profile>

     /**
      * Retrieves a list of user profiles not associated with a specific meter.
      *
      * @param meterId The unique identifier of the meter
      * @return A list of profiles representing users not associated with the specified meter
      */
     fun getUsersWithoutMeter(meterId: String): List<Profile>

     /**
      * Determines if a specific meter is currently assigned to a user.
      *
      * @param meterUserAssignment the assignment details containing the meter ID and user ID
      * @return true if the meter is assigned to the specified user, false otherwise
      */
     fun isMeterAssignedToUser(meterUserAssignment: MeterUserAssignment): Boolean

     /**
      * Gets the user assigned to a specific meter.
      *
      * @param meterId The unique identifier of the meter
      * @return The profile of the user assigned to the meter, or null if the meter is not assigned
      */
     fun getUserByMeter(meterId: String): Profile?
}