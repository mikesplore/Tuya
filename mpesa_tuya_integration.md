# M-Pesa Integration with Tuya Smart Meter

This document explains how M-Pesa payments are integrated with the Tuya Smart Meter system to automatically add balance to meters after successful payments.

## Overview

The integration follows this flow:

1. User initiates an M-Pesa payment for a specific meter
2. M-Pesa processes the payment and sends a callback to our system
3. The system processes the callback and automatically adds the balance to the Tuya smart meter
4. The payment record is updated with the meter balance information

## Payment Flow

### 1. Initiate Payment

```
POST /mpesa/payments
```

**Request Body**

```json
{
  "amount": 500.0,
  "phoneNumber": "254712345678",
  "meterId": "uuid-of-meter",
  "description": "Payment for electricity"
}
```

**Response**

```json
{
  "success": true,
  "message": "Payment initiated",
  "merchantRequestId": "string",
  "checkoutRequestId": "string",
  "mpesaTransactionId": "string"
}
```

### 2. M-Pesa Callback

M-Pesa sends a callback to our system after the payment is processed:

```
POST /mpesa/callback
```

The system processes this callback and:

1. Updates the M-Pesa transaction record
2. Updates the meter payment record
3. If the payment was successful, connects to the Tuya Cloud and adds balance to the meter
4. Records the balance before and after the operation

### 3. Check Payment Status

```
GET /mpesa/payments/{id}
```

**Response**

```json
{
  "id": "string",
  "amount": 500.0,
  "phoneNumber": "254712345678",
  "status": "COMPLETED",
  "mpesaReceiptNumber": "string",
  "transactionDate": "2023-07-01T12:00:00",
  "meterName": "Smart Meter XYZ",
  "balanceBefore": 100.0,
  "balanceAfter": 600.0,
  "unitsAdded": 500.0
}
```

## Technical Implementation

The integration works by:

1. In the M-Pesa callback handler, retrieving the SmartMeterService from the application context
2. Using the SmartMeterService to:
   - Get the current balance of the meter
   - Add the payment amount to the meter using the `charge_money` command
   - Get the updated balance
3. Updating the payment record with the balance information

## Supported Tuya Commands

The Tuya Smart Meter supports the following billing-related commands:

- `charge_money`: Adds monetary balance to the meter
- `charge_token`: Adds prepaid tokens to the meter
- `balance_energy`: Gets the remaining energy balance (kWh)
- `balance`: Gets the remaining monetary balance
- `forward_energy_total`: Gets the total energy consumed
- `electric_total`: Gets the total energy purchased
- `goods_price`: Sets the price per kWh
- `unit_price`: Sets the price per kWh (string format)
- `clear_energy`: Resets the energy balance
- `prepayment_switch`: Enables/disables prepaid mode

## Fallback Mechanisms

If the automatic balance update fails:

1. The payment is still recorded as completed (if M-Pesa confirms it)
2. Administrators can manually add balance to the meter using the dashboard
3. The system periodically checks for completed payments that haven't updated the meter and retries
