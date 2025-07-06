# Device Management API Documentation

This document outlines the API endpoints for managing smart electric meters, including the billing-related operations.

## Base URL

All URLs referenced in the documentation have the following base:

```
http://your-api-domain/
```

## Authentication

Most endpoints do not require authentication as they are meant to be accessed by both authenticated and unauthenticated users.

## Endpoints

### Get All Devices

```
GET /devices
```

Retrieves a list of all devices from the database, then updates them from the Tuya Cloud in the background.

**Response**

```json
{
  "devices": [
    {
      "id": "string",
      "deviceId": "string",
      "name": "string",
      "productName": "string",
      "description": "string",
      "location": "string",
      "active": true,
      "createdAt": "2023-07-01T12:00:00",
      "updatedAt": "2023-07-01T12:00:00"
    }
  ],
  "count": 1
}
```

### Get Device by ID

```
GET /devices/{id}
```

Retrieves detailed information about a specific device, including its status from the Tuya Cloud.

**Response**

```json
{
  "device": {
    "id": "string",
    "name": "string",
    "productName": "string",
    "online": true
  },
  "status": [
    {
      "code": "string",
      "value": "string"
    }
  ],
  "specifications": {
    "functions": [
      {
        "code": "string",
        "type": "string",
        "values": "string"
      }
    ]
  },
  "summary": {
    "totalConsumption": 123.45,
    "creditRemaining": 100,
    "balance": 500.0,
    "totalEnergyPurchased": 1000.0,
    "creditStatus": "Credit Level Good",
    "batteryLevel": 80,
    "batteryStatus": "Battery Level Good"
  }
}
```

### Register a New Device

```
POST /devices
```

Registers a new device in the system.

**Request Body**

```json
{
  "deviceId": "string",
  "name": "string",
  "productName": "string",
  "description": "string",
  "location": "string"
}
```

**Response**

```json
{
  "id": "string",
  "deviceId": "string",
  "name": "string",
  "productName": "string",
  "description": "string",
  "location": "string",
  "active": true,
  "createdAt": "2023-07-01T12:00:00",
  "updatedAt": "2023-07-01T12:00:00"
}
```

### Update Device

```
PUT /devices/{id}
```

Updates device information.

**Request Body**

```json
{
  "name": "string",
  "productName": "string",
  "description": "string",
  "location": "string",
  "active": true
}
```

**Response**

```json
{
  "id": "string",
  "deviceId": "string",
  "name": "string",
  "productName": "string",
  "description": "string",
  "location": "string",
  "active": true,
  "createdAt": "2023-07-01T12:00:00",
  "updatedAt": "2023-07-01T12:00:00"
}
```

### Delete Device

```
DELETE /devices/{id}
```

Deletes a device from the system.

**Response**

```json
{
  "message": "Device deleted"
}
```

## Billing Operations

### Add Balance (Money)

```
POST /devices/{id}/add-balance
```

Adds monetary balance to a device.

**Request Body**

```json
{
  "amount": 500.0
}
```

**Response**

```json
{
  "success": true,
  "message": "Added 500.0 money units to Smart Meter XYZ",
  "deviceId": "string",
  "command": "charge_money",
  "value": 500.0
}
```

### Add Token

```
POST /devices/{id}/add-token
```

Applies a prepaid token to a device.

**Request Body**

```json
{
  "token": "string"
}
```

**Response**

```json
{
  "success": true,
  "message": "Token applied successfully to Smart Meter XYZ",
  "deviceId": "string",
  "command": "charge_token",
  "value": "string"
}
```

### Get Balance

```
GET /devices/{id}/balance
```

Retrieves the current balance of a device.

**Response**

```json
{
  "success": true,
  "deviceId": "string",
  "energyBalance": 100.0,
  "moneyBalance": 500.0
}
```

### Get Energy Usage

```
GET /devices/{id}/usage
```

Retrieves energy usage data for a device.

**Response**

```json
{
  "success": true,
  "deviceId": "string",
  "totalEnergyUsed": 1234.5,
  "totalEnergyPurchased": 2000.0,
  "monthlyEnergy": 150.0,
  "dailyEnergy": 5.0
}
```

### Set Unit Price

```
POST /devices/{id}/set-price
```

Sets the price per unit of energy.

**Request Body**

```json
{
  "price": 20.0,
  "currencySymbol": "Ksh"
}
```

**Response**

```json
{
  "success": true,
  "message": "Command 'goods_price' sent successfully to Smart Meter XYZ",
  "deviceId": "string",
  "command": "goods_price",
  "value": 20.0
}
```

### Reset Balance

```
POST /devices/{id}/reset-balance
```

Resets the energy balance to zero.

**Response**

```json
{
  "success": true,
  "message": "Command 'clear_energy' sent successfully to Smart Meter XYZ",
  "deviceId": "string",
  "command": "clear_energy",
  "value": true
}
```

### Toggle Prepayment Mode

```
POST /devices/{id}/prepayment-mode?enable=true
```

Enables or disables prepayment mode.

**Query Parameters**

- `enable`: Boolean (default: true)

**Response**

```json
{
  "success": true,
  "message": "Command 'prepayment_switch' sent successfully to Smart Meter XYZ",
  "deviceId": "string",
  "command": "prepayment_switch",
  "value": true
}
```

## Device Assignment

### Assign Device to User

```
POST /devices/{id}/assign
```

Assigns a device to a user.

**Request Body**

```json
{
  "userId": "string"
}
```

**Response**

```json
{
  "userId": "string",
  "meterId": "string",
  "assignedAt": "2023-07-01T12:00:00"
}
```

### Unassign Device from User

```
DELETE /devices/{id}/assign/{userId}
```

Removes a device assignment from a user.

**Response**

```json
{
  "message": "Device unassigned from user"
}
```

### Get Users Assigned to Device

```
GET /devices/{id}/users
```

Lists all users assigned to a device.

**Response**

```json
[
  {
    "id": "string",
    "email": "user@example.com",
    "firstName": "string",
    "lastName": "string",
    "role": "USER"
  }
]
```

## Maintenance Operations

### Force Refresh Devices from Cloud

```
POST /devices/refresh
```

Forces a refresh of all devices from the Tuya Cloud.

**Response**

```json
{
  "message": "Device sync completed",
  "total": 5,
  "new": 1,
  "updated": 4
}
```

## Error Responses

All endpoints return standard error responses in the following format:

```json
{
  "error": "error_code",
  "message": "Error description"
}
```

Common error codes:

- `device_not_found`: The requested device does not exist
- `connection_error`: Failed to connect to Tuya Cloud
- `billing_error`: Error during billing operation
- `internal_error`: Unexpected server error
