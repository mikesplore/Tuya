package com.mike.domain.repository.user

import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.ProfilePicture
import com.mike.domain.model.user.RegisterRequest
import com.mike.domain.model.user.User

/**
 * UserRepository interface defines the contract for user-related operations.
 * It provides methods to manage user data, including retrieval, creation, updating, and deletion of users.
 */
interface UserRepository {

    /**
     * Retrieves a user by their email address.
     *
     * @param email the email address of the user to retrieve
     * @return the user associated with the given email, or null if no user exists
     */
    fun findByEmail(email: String): User?
    /**
     * Retrieves a user by their unique ID.
     *
     * @param userId the unique identifier of the user to retrieve
     * @return the user associated with the given ID, or null if the user does not exist
     */
    fun findById(userId: Int): User?
    /**
     * Retrieves the user profile associated with the specified user ID.
     *
     * @param userId The unique identifier of the user whose profile is being requested.
     * @return The user's profile if found; otherwise, returns null.
     */
    fun fundUserProfile(userId: Int): Profile?
    /**
     * Retrieves the role of a user based on their unique identifier.
     *
     * @param userId The unique identifier of the user whose role is to be retrieved.
     * @return The role of the user as a string if found, or null if the user's role cannot be determined.
     */
    fun findUserRole(userId: Int): String?
    /**
     * Retrieves a list of all user profiles.
     *
     * @return A list of Profile objects representing all the users.
     */
    fun getAllUsers(): List<Profile>
    /**
     * Creates a new user with the provided registration details.
     *
     * @param user The registration details of the user, including email, password, and optional personal information.
     * @return A pair where the first value indicates the success status of the operation (true if successful, false otherwise),
     *         and the second value contains an error message if applicable, or null if the operation was successful.
     */
    fun createUser(user: RegisterRequest): Pair<Boolean, String?>
    /**
     * Updates the details of an existing user in the system.
     *
     * @param updatedUser The profile containing the updated user information.
     * @return A pair where the first value indicates if the update was successful (true/false),
     * and the second value is a nullable message providing additional details, such as error information
     * if the update was unsuccessful.
     */
    fun updateUser(updatedUser: Profile): Pair<Boolean, String?>
    /**
     * Deletes a user from the system based on their unique identifier.
     *
     * @param userId the unique identifier of the user to be deleted
     * @return a pair where the first value is a boolean indicating the success of the deletion
     * (true if successfully deleted, false otherwise), and the second value is an optional message
     * providing details about the operation, or null if no error occurred
     */
    fun deleteUser(userId: Int): Pair<Boolean, String?>
    /**
     * Uploads a profile picture for the specified user.
     *
     * @param userId The unique identifier of the user whose profile picture is being updated.
     * @param filename The filename of the profile picture.
     * @param contentType The content type (MIME type) of the profile picture.
     * @param imageData The binary data of the profile picture.
     * @return A pair where the first element is a boolean indicating the success of the operation,
     * and the second element is an optional error message, or null if the operation was successful.
     */
    fun uploadProfilePicture(userId: Int, filename: String, contentType: String, imageData: ByteArray): Pair<Boolean, String?>
    /**
     * Retrieves the profile picture for a given user by their user ID.
     *
     * @param userId The unique identifier of the user whose profile picture is to be retrieved.
     * @return The profile picture of the user as a ProfilePicture object, or null if no profile picture exists.
     */
    fun getProfilePicture(userId: Int): ProfilePicture?

}
