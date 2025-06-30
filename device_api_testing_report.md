# Tuya Device API Testing Report

## Summary

This report summarizes the testing of the Device API endpoints in the Tuya application. The tests were conducted to verify that all device-related endpoints work correctly.

## Test Environment

- Server: Running on http://localhost:8080
- Authentication: JWT token-based authentication is used

## Authentication Flow

1. Register a user: POST /auth/register
2. Login to get JWT token: POST /auth/login
3. Use token in Authorization header for subsequent requests

## Device Endpoints

The following endpoints were tested:

### GET /devices
- Returns a list of all devices 
- For admin users: Returns all devices
- For regular users: Returns only devices assigned to the user
- Response format: `{ "devices": [...], "count": n }`

### POST /devices/refresh
- Admin only endpoint
- Synchronizes devices from Tuya Cloud
- Returns a summary of the sync operation

### GET /devices/{id}
- Returns details for a specific device
- Access control: Admin or assigned user

### POST /devices
- Admin only endpoint
- Creates a new device
- Requires a valid deviceId that exists in Tuya Cloud

### PUT /devices/{id}
- Admin only endpoint
- Updates device information
- Fields: name, productName, description, location, active

### DELETE /devices/{id}
- Admin only endpoint
- Deletes a device from the system

### POST /devices/{id}/commands
- Sends a command to a device
- Access control: Admin or assigned user
- Request body: `{ "code": "command_code", "value": JsonElement }`

### POST /devices/{id}/add-balance
- Adds balance to a smart meter device
- Access control: Admin or assigned user
- Request body: `{ "amount": double }`

### POST /devices/{id}/assign
- Admin only endpoint
- Assigns a device to a user
- Request body: `{ "userId": "user_id" }`

### DELETE /devices/{id}/assign/{userId}
- Admin only endpoint
- Removes a device assignment from a user

### GET /devices/{id}/users
- Admin only endpoint
- Lists all users assigned to a device

## Test Results

The testing revealed that the API endpoints are functioning as expected with proper authentication and authorization. The endpoints correctly handle different roles (admin vs. regular user) and apply appropriate access controls.

## Notes

- To fully test all endpoints, an admin user is required
- The command endpoint requires proper formatting of the JSON value
- There's synchronization between Tuya Cloud and the local database
- Error handling is implemented consistently across endpoints
