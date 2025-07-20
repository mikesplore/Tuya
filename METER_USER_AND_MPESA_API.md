# Meter User Assignment & Mpesa Payment API Documentation

This guide explains how to use the Meter User Assignment API and Mpesa Payment API, and how they work together for meter recharge operations. It is intended for frontend developers integrating with the Tuya backend.

---

## Overview

- **Meter User Assignment API**: Manages which users are assigned to which meters. Only assigned users can recharge a meter.
- **Mpesa Payment API**: Handles mobile payments for meter recharging via M-Pesa. Payments are linked to both the user and the meter.

**Relationship:**
- A user must be assigned to a meter before they can recharge it using Mpesa.
- The assignment ensures only authorized users can initiate payments for a specific meter.

---

## Typical Flow

1. **Assign Meter to User**
   - Use `/meter-user/assign` to assign a meter to a user.
   - Check assignment status with `/meter-user/is-assigned?meterId={meterId}&userId={userId}`.
2. **User Initiates Payment**
   - Use `/mpesa/payment` to start a recharge for an assigned meter.
   - The backend verifies assignment before processing payment.
3. **Check Payment Status**
   - Use `/mpesa/status/{checkoutRequestId}` to check the status of a payment.
   - Use `/mpesa/transactions` to view all payment history.

---

## Meter User Assignment API Endpoints

### POST `/meter-user/assign`
Assign a meter to a user.
- **Payload:**
  ```json
  {
    "meterId": "string",
    "userId": 1,
    "isAssigned": true
  }
  ```
- **Response:** `{ "success": true }`

### POST `/meter-user/unassign`
Unassign a meter from a user.
- **Payload:** Same as assign, but `isAssigned: false`.
- **Response:** `{ "success": true }`

### GET `/meter-user/user/{userId}/meters`
Get meters assigned to a user.
- **Response:** `Meter[]`

### GET `/meter-user/meter/{meterId}/users`
Get users assigned to a meter.
- **Response:** `Profile[]`

### GET `/meter-user/is-assigned?meterId={meterId}&userId={userId}`
Check if a meter is assigned to a user.
- **Response:** `{ "isAssigned": true }`

### POST `/meter-user/generate-assignments`
Generate user-meter assignments (admin only).
- **Response:** `{ "success": true }`

---

## Mpesa Payment API Endpoints

### POST `/mpesa/payment`
Initiate Mpesa payment for a meter recharge.
- **Payload:**
  ```json
  {
    "amount": 100.0,
    "phoneNumber": "254712345678",
    "meterId": "meter123",
    "userId": 1,
    "accountReference": "optional",
    "description": "optional"
  }
  ```
- **Response:**
  ```json
  {
    "success": true,
    "message": "STK push sent",
    "merchantRequestId": "...",
    "checkoutRequestId": "...",
    "mpesaTransactionId": "..."
  }
  ```

### GET `/mpesa/status/{checkoutRequestId}`
Get the status of a payment by checkoutRequestId.
- **Response:** `MpesaTransaction`

### POST `/mpesa/callback`
Endpoint for M-Pesa to send payment results (used by backend).
- **Payload:** `StkCallbackResponse` (see OpenAPI spec)
- **Response:** `{ "ResultCode": 0, "ResultDesc": "Success" }`

### GET `/mpesa/transactions`
Get all Mpesa transactions.
- **Response:** `MpesaTransaction[]`

---

## Data Models

### MeterUserAssignment
```typescript
interface MeterUserAssignment {
  meterId: string;
  userId: number;
  isAssigned: boolean;
}
```

### MpesaTransaction
```typescript
interface MpesaTransaction {
  id: number;
  phoneNumber: string;
  amount: number;
  merchantRequestId: string;
  checkoutRequestId: string;
  responseCode: string;
  responseDescription: string;
  customerMessage: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}
```

---

## Best Practices

- Always check if a user is assigned to a meter before allowing recharge.
- Use the assignment endpoints to manage access control for meters.
- After initiating payment, poll `/mpesa/status/{checkoutRequestId}` to confirm transaction completion.
- Use `/mpesa/transactions` for displaying payment history to users.
- Handle errors and edge cases (e.g., unassigned meter, failed payment) gracefully in the frontend.

---

## Example Flow (Frontend)

1. **User logs in and views assigned meters:**
   - GET `/meter-user/user/{userId}/meters`
2. **User selects a meter and initiates recharge:**
   - POST `/mpesa/payment` with meterId and userId
3. **Frontend polls for payment status:**
   - GET `/mpesa/status/{checkoutRequestId}`
4. **Frontend displays transaction history:**
   - GET `/mpesa/transactions`

---


