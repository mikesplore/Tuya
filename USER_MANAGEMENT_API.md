# User Management API Documentation

This document describes the user management endpoints for the Tuya backend, including what actions an admin can perform and what regular users are restricted from doing.

---

## Overview

User management allows for creating, updating, deleting, and viewing user profiles. Some actions are restricted to admin users only.

---

## Endpoints

### 1. Get All Users
- **Endpoint:** `GET /users`
- **Access:** Admin only
- **Description:** Returns a list of all users in the system.
- **Response:** `Profile[]`

### 2. Get Current User Profile
- **Endpoint:** `GET /users/me`
- **Access:** Authenticated users
- **Description:** Returns the profile of the currently authenticated user.
- **Response:** `Profile`

### 3. Get User by ID
- **Endpoint:** `GET /users/{id}`
- **Access:** Admin or self
- **Description:** Returns the profile of a user by their ID. Admins can view any user; regular users can only view their own profile.
- **Response:** `Profile`

### 4. Create New User
- **Endpoint:** `POST /users`
- **Access:** Admin only
- **Description:** Creates a new user account.
- **Request Body:**
  ```json
  {
    "email": "string",
    "password": "string",
    "phoneNumber": "string (optional)",
    "firstName": "string (optional)",
    "lastName": "string (optional)"
  }
  ```
- **Response:** `Profile`

### 5. Update User Profile
- **Endpoint:** `PUT /users/{id}`
- **Access:** Admin or self
- **Description:** Updates a user's profile. Admins can update any user; regular users can only update their own profile.
- **Request Body:**
  ```json
  {
    "userId": 1,
    "firstName": "string (optional)",
    "lastName": "string (optional)",
    "email": "string (optional)",
    "phoneNumber": "string (optional)",
    "userRole": "string",
    "profilePictureUrl": "string (optional)"
  }
  ```
- **Response:** `Profile`

### 6. Delete User
- **Endpoint:** `DELETE /users/{id}`
- **Access:** Admin only
- **Description:** Deletes a user account.
- **Response:**
  ```json
  { "message": "User deleted" }
  ```

### 7. Upload Profile Picture
- **Endpoint:** `POST /users/{id}/profile-picture`
- **Access:** Authenticated users (self or admin)
- **Description:** Uploads a profile image for a user. Accepts multipart/form-data.
- **Response:**
  ```json
  { "message": "Profile picture uploaded successfully" }
  ```

### 8. Get Profile Picture
- **Endpoint:** `GET /users/{id}/profile-picture`
- **Access:** Authenticated users
- **Description:** Returns the profile image for a user (image binary).
- **Response:** Image file

---

## Admin Permissions
- Can view all users
- Can create new users
- Can update any user's profile
- Can delete any user
- Can view and upload profile pictures for any user

## Regular User Permissions
- Can view their own profile
- Can update their own profile
- Can upload and view their own profile picture
- Cannot view other users' profiles (except their own)
- Cannot create or delete users

---

## Example Usage

### Get Current User Profile
```http
GET /users/me
Authorization: Bearer <token>
```

### Create New User (Admin Only)
```http
POST /users
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "email": "newuser@example.com",
  "password": "password123"
}
```

### Update Own Profile
```http
PUT /users/1
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": 1,
  "firstName": "John",
  "lastName": "Doe"
}
```

---

For more details, see the OpenAPI spec in `/src/main/resources/openapi/users.yaml`.

