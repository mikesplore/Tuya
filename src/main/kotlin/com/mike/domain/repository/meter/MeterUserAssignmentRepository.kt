package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.domain.model.user.Profile


interface MeterUserAssignmentRepository {
     /**
      * Assigns a meter to a user based on the provided meter and user details.
      *
      * @param meterUserAssignment an instance of MeterUserAssignment containing
      *                            the meterId of the meter, userId of the user,
      *                            and a boolean value indicating if the meter
      *                            should be assigned or unassigned.
      */
     fun assignMeterToUser(meterUserAssignment: MeterUserAssignment)

     /**
      * Unassigns a meter from a user based on the provided meter-user assignment details.
      *
      * @param meterUserAssignment The `MeterUserAssignment` object containing details about the specific
      * meter and user relationship to be unassigned. It includes the meter ID, user ID, and assignment status.
      */
     fun unassignMeterFromUser(meterUserAssignment: MeterUserAssignment)

     /**
      * Retrieves a list of meters assigned to a specific user.
      *
      * @param userId The unique identifier of the user for whom the assigned meters are to be retrieved.
      * @return A list of meters assigned to the specified user.
      */
     fun getAssignedMetersByUser(userId: Int): List<Meter>

     /**
      * Retrieves a list of user profiles associated with a specific meter.
      *
      * @param meterId The unique identifier of the meter for which to retrieve associated users.
      * @return A list of profiles representing the users associated with the specified meter.
      */
     fun getUsersByMeter(meterId: String): List<Profile>

     /**
      * Determines if a specific meter is currently assigned to a user based on the given assignment details.
      *
      * @param meterUserAssignment the assignment details containing the meter ID, user ID, and assignment status
      * @return true if the meter is assigned to the specified user, false otherwise
      */
     fun isMeterAssignedToUser(meterUserAssignment: MeterUserAssignment): Boolean

     /**
      * Generates an assignment of meters to users. The implementation details of the
      * assignment process are handled internally.
      *
      * This function may include creating associations between users and meters,
      * managing existing assignments, and ensuring all logical constraints for the
      * assignments are respected. It does not return a value but modifies the internal
      * state of the related data structures or system as required.
      *
      * Use this method to perform a bulk or automated assignment process where
      * individual meter-to-user assignments are not explicitly specified.
      */
     fun generateUserMeterAssignment()

}