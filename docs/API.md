# API Documentation

## Table of Contents
- [Authentication](#authentication)
- [Users](#users)
- [Meters](#meters)
- [Meter User Assignment](#meter-user-assignment)
- [M-Pesa Payments](#m-pesa-payments)

## Types

### Common Types

```typescript
interface MessageResponse {
  message: string;
}

interface Profile {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phoneNumber: string | null;
  userRole: string;
  createdAt: string; // ISO date-time
  updatedAt: string; // ISO date-time
  profilePictureUrl: string | null;
}

interface Meter {
  meterId: string;
  name: string;
  productName: string | null;
  description: string | null;
  location: string | null;
  active: boolean;
  createdAt: string; // ISO date-time
  updatedAt: string; // ISO date-time
}

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
  createdAt: string; // ISO date-time
  updatedAt: string; // ISO date-time
}
```

## Users

### Get All Users
```typescript
// GET /users
// Requires: Bearer token with admin role
interface Response = Profile[]
```

### Create User
```typescript
// POST /users
// Requires: Bearer token with admin role
interface RegisterRequest {
  email: string;
  password: string;
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
}

interface Response = Profile
```

### Get User by ID
```typescript
// GET /users/{id}
// Requires: Bearer token (admin or self)
interface Response = Profile
```

### Update User
```typescript
// PUT /users/{id}
// Requires: Bearer token (admin or self)
interface ProfileUpdateRequest {
  userId: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
  userRole: string;
  profilePictureUrl?: string;
}

interface Response = Profile
```

### Delete User
```typescript
// DELETE /users/{id}
// Requires: Bearer token with admin role
interface Response = MessageResponse
```

### Profile Picture
```typescript
// POST /users/{id}/profile-picture
// Requires: Bearer token, multipart/form-data
FormData with 'file' field

// GET /users/{id}/profile-picture
// Requires: Bearer token
// Returns: Image binary data
```

## Meters

### Get All Meters
```typescript
// GET /meters
interface Response = Meter[]
```

### Create Meter
```typescript
// POST /meters
interface MeterCreationRequest {
  meterId: string;
  name: string;
  productName?: string;
  description?: string;
  location?: string;
  active: boolean;
}

interface Response = MessageResponse
```

### Get Meter by ID
```typescript
// GET /meters/{id}
interface Response = Meter
```

### Update Meter
```typescript
// PUT /meters/{id}
interface Request = MeterCreationRequest
interface Response = MessageResponse
```

### Delete Meter
```typescript
// DELETE /meters/{id}
interface Response = MessageResponse
```

## Meter User Assignment

### Assign Meter to User
```typescript
// POST /meter-user/assign
interface MeterUserAssignment {
  meterId: string;
  userId: number;
  isAssigned: boolean;
}

interface Response = {
  success: boolean;
}
```

### Unassign Meter from User
```typescript
// POST /meter-user/unassign
interface Request = MeterUserAssignment
interface Response = {
  success: boolean;
}
```

### Get User's Meters
```typescript
// GET /meter-user/user/{userId}/meters
interface Response = Meter[]
```

### Get Meter's Users
```typescript
// GET /meter-user/meter/{meterId}/users
interface Response = Profile[]
```

### Check Assignment
```typescript
// GET /meter-user/is-assigned?meterId={meterId}&userId={userId}
interface Response = {
  isAssigned: boolean;
}
```

## M-Pesa Payments

### Initiate Payment
```typescript
// POST /mpesa/payment
interface PaymentRequest {
  amount: number;
  phoneNumber: string;
  meterId: string;
  userId: number;
  accountReference?: string;
  description?: string;
}

interface PaymentResponse {
  success: boolean;
  message: string;
  merchantRequestId?: string;
  checkoutRequestId?: string;
  mpesaTransactionId?: string;
}
```

### Check Payment Status
```typescript
// GET /mpesa/status/{checkoutRequestId}
interface Response = MpesaTransaction
```

### Get All Transactions
```typescript
// GET /mpesa/transactions
interface Response = MpesaTransaction[]
```

## Example Usage (React + TypeScript)

```typescript
// Example using axios
import axios from 'axios';

const api = axios.create({
  baseURL: 'YOUR_API_BASE_URL',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Example: Initiate M-Pesa payment
const initiateMpesaPayment = async (paymentData: PaymentRequest) => {
  try {
    const response = await api.post<PaymentResponse>('/mpesa/payment', paymentData);
    return response.data;
  } catch (error) {
    console.error('Payment initiation failed:', error);
    throw error;
  }
};

// Example: Get user profile
const getUserProfile = async (userId: number) => {
  try {
    const response = await api.get<Profile>(`/users/${userId}`);
    return response.data;
  } catch (error) {
    console.error('Failed to fetch user profile:', error);
    throw error;
  }
};
```

## Error Handling

All endpoints may return these status codes:

- `200/201`: Success
- `400`: Bad Request (invalid input)
- `401`: Unauthorized (missing or invalid token)
- `403`: Forbidden (insufficient permissions)
- `404`: Not Found
- `500`: Internal Server Error

Error responses follow the `MessageResponse` format:
```typescript
{
  message: string
}
```
