# Tuya Smart Meter API Documentation

This document provides detailed information on available API endpoints for the Tuya Smart Meter backend application. It is intended for frontend developers who need to interact with the backend services.

## Table of Contents
- [Overview](#overview)
- [Base URL](#base-url)
- [Authentication](#authentication)
- [Device Management Endpoints](#device-management-endpoints)
- [User Management Endpoints](#user-management-endpoints)
- [Error Handling](#error-handling)
- [Testing](#testing)

## Overview

The Tuya Smart Meter API provides functionality to manage smart meters (devices) connected to the Tuya Cloud platform. The API allows users to perform operations like:

- Retrieving device lists and details
- Creating, updating, and deleting devices
- Assigning devices to users
- Sending commands to devices
- Adding balance to devices
- Managing user accounts

## Base URL

All API endpoints are relative to the base URL:

```
http://localhost:8080
```

For production deployments, this URL will be replaced with the appropriate domain.

## Authentication

The API uses JWT (JSON Web Token) based authentication for user management endpoints. **Important: Device endpoints are NOT protected and can be accessed without authentication.**

### Login

```
POST /auth/login
```

Request body:
```json
{
  "email": "user@example.com",
  "password": "user_password"
}
```

Response:
```json
{
  "token": "jwt_token_string",
  "userId": "user_id",
  "role": "USER" | "ADMIN"
}
```

Store this token for authenticated requests.

## Device Management Endpoints

**Important Note**: All device endpoints are now publicly accessible and do not require authentication.

### Get All Devices

```
GET /devices
```

Response:
```json
{
  "devices": [
    {
      "id": "device_id",
      "deviceId": "tuya_device_id",
      "name": "Device Name",
      "productName": "Product Name",
      "description": "Device Description",
      "location": "Device Location",
      "active": true,
      "createdAt": "2025-06-25T10:00:00Z",
      "updatedAt": "2025-06-25T10:00:00Z"
    }
  ],
  "count": 1
}
```

### Get Device by ID

```
GET /devices/{id}
```

Response:
```json
{
  "id": "device_id",
  "deviceId": "tuya_device_id",
  "name": "Device Name",
  "productName": "Product Name",
  "description": "Device Description",
  "location": "Device Location",
  "active": true,
  "createdAt": "2025-06-25T10:00:00Z",
  "updatedAt": "2025-06-25T10:00:00Z",
  "device": {
    // Additional device details from Tuya Cloud
  },
  "status": {
    // Current device status
  }
}
```

### Create Device

```
POST /devices
```

Request body:
```json
{
  "deviceId": "tuya_device_id",
  "name": "Device Name",
  "productName": "Product Name",
  "description": "Device Description",
  "location": "Device Location"
}
```

Response:
```json
{
  "id": "device_id",
  "deviceId": "tuya_device_id",
  "name": "Device Name",
  "productName": "Product Name",
  "description": "Device Description",
  "location": "Device Location",
  "active": true,
  "createdAt": "2025-06-25T10:00:00Z",
  "updatedAt": "2025-06-25T10:00:00Z"
}
```

### Update Device

```
PUT /devices/{id}
```

Request body:
```json
{
  "name": "Updated Device Name",
  "description": "Updated Description",
  "location": "Updated Location",
  "active": true
}
```

Response:
```json
{
  "id": "device_id",
  "deviceId": "tuya_device_id",
  "name": "Updated Device Name",
  "productName": "Product Name",
  "description": "Updated Description",
  "location": "Updated Location",
  "active": true,
  "createdAt": "2025-06-25T10:00:00Z",
  "updatedAt": "2025-06-30T10:00:00Z"
}
```

### Delete Device

```
DELETE /devices/{id}
```

Response:
```json
{
  "message": "Device deleted"
}
```

### Refresh Devices from Cloud

```
POST /devices/refresh
```

Response:
```json
{
  "message": "Device sync completed",
  "total": 5,
  "new": 2,
  "updated": 3
}
```

### Send Command to Device

```
POST /devices/{id}/commands
```

Request body:
```json
{
  "code": "switch_1",
  "value": {
    "type": "boolean",
    "value": true
  }
}
```

Response:
```json
{
  "success": true,
  "result": {
    // Command result details
  }
}
```

### Add Balance to Device

```
POST /devices/{id}/add-balance
```

Request body:
```json
{
  "amount": 50.0
}
```

Response:
```json
{
  "success": true,
  "balance": 150.0
}
```

### Get Users Assigned to Device

```
GET /devices/{id}/users
```

Response:
```json
[
  {
    "id": "user_id",
    "name": "User Name",
    "email": "user@example.com"
  }
]
```

### Assign Device to User

```
POST /devices/{id}/assign
```

Request body:
```json
{
  "userId": "user_id"
}
```

Response:
```json
{
  "id": "assignment_id",
  "userId": "user_id",
  "meterId": "device_id",
  "createdAt": "2025-06-30T10:00:00Z"
}
```

### Unassign Device from User

```
DELETE /devices/{id}/assign/{userId}
```

Response:
```json
{
  "message": "Device unassigned from user"
}
```

## User Management Endpoints

**Note**: User management endpoints require authentication.

### Get All Users (Admin only)

```
GET /users
```

Headers:
```
Authorization: Bearer jwt_token
```

Response:
```json
{
  "users": [
    {
      "id": "user_id",
      "name": "User Name",
      "email": "user@example.com",
      "role": "USER",
      "createdAt": "2025-06-25T10:00:00Z"
    }
  ],
  "count": 1
}
```

### Get User by ID (Admin or self)

```
GET /users/{id}
```

Headers:
```
Authorization: Bearer jwt_token
```

Response:
```json
{
  "id": "user_id",
  "name": "User Name",
  "email": "user@example.com",
  "role": "USER",
  "createdAt": "2025-06-25T10:00:00Z"
}
```

### Create User (Admin only)

```
POST /users
```

Headers:
```
Authorization: Bearer jwt_token
```

Request body:
```json
{
  "name": "New User",
  "email": "newuser@example.com",
  "password": "user_password",
  "role": "USER"
}
```

Response:
```json
{
  "id": "user_id",
  "name": "New User",
  "email": "newuser@example.com",
  "role": "USER",
  "createdAt": "2025-06-30T10:00:00Z"
}
```

### Update User (Admin or self)

```
PUT /users/{id}
```

Headers:
```
Authorization: Bearer jwt_token
```

Request body:
```json
{
  "name": "Updated User Name",
  "email": "updated@example.com"
}
```

Response:
```json
{
  "id": "user_id",
  "name": "Updated User Name",
  "email": "updated@example.com",
  "role": "USER",
  "createdAt": "2025-06-25T10:00:00Z"
}
```

### Delete User (Admin only)

```
DELETE /users/{id}
```

Headers:
```
Authorization: Bearer jwt_token
```

Response:
```json
{
  "message": "User deleted"
}
```

### Get User's Devices

```
GET /users/{id}/devices
```

Headers:
```
Authorization: Bearer jwt_token
```

Response:
```json
[
  {
    "id": "device_id",
    "deviceId": "tuya_device_id",
    "name": "Device Name",
    "productName": "Product Name",
    "description": "Device Description",
    "location": "Device Location",
    "active": true
  }
]
```

## Error Handling

All API endpoints follow consistent error response formats:

```json
{
  "error": "error_code",
  "message": "Error description"
}
```

Common error codes:
- `connection_error`: Failed to connect to Tuya Cloud
- `device_not_found`: Device not found
- `device_exists`: Device already exists
- `user_not_found`: User not found
- `invalid_input`: Invalid input data
- `unauthorized`: Unauthorized access
- `forbidden`: Forbidden access

HTTP status codes:
- 200 OK: Successful operation
- 201 Created: Resource created
- 400 Bad Request: Invalid input
- 401 Unauthorized: Authentication required
- 403 Forbidden: Insufficient permissions
- 404 Not Found: Resource not found
- 409 Conflict: Resource conflict
- 500 Internal Server Error: Server-side error

## Testing

The repository includes scripts for testing the API endpoints:

- `test_devices_no_auth.sh`: Tests all device endpoints without authentication
- `test_endpoints.sh`: Tests all endpoints with authentication
- `test_users.sh`: Tests user management endpoints with authentication

To run the tests:

```bash
# Test device endpoints without authentication
./test_devices_no_auth.sh

# Test all endpoints with authentication
./test_endpoints.sh

# Test user management endpoints
./test_users.sh
```

## Notes for Frontend Developers

1. **Device Endpoints**: All device-related endpoints (`/devices/*`) are now publicly accessible without authentication. This allows direct access to device data and operations from your frontend application without requiring a user to be logged in.

2. **Error Handling**: Implement proper error handling in your frontend application based on the HTTP status codes and error response format. Always display meaningful error messages to users.

3. **Performance**: To improve performance, consider implementing client-side caching for frequently accessed device data.

4. **Security**: Although device endpoints are open, user management endpoints still require authentication. Ensure proper token management in your frontend application.

5. **Development Workflow**: 
   - Use the `/health` endpoint to check if the server is running
   - For testing, you can use the provided test scripts or tools like Postman
   - Implement proper loading states in your UI while waiting for API responses
