# Tuya API Endpoints Documentation

This documentation is designed for frontend developers (React/TypeScript) integrating with the Tuya backend. It covers authentication, user, meter, meter-user assignment, and Mpesa payment endpoints, including TypeScript interfaces and example usage.

---

## Authentication

### POST /auth/login
Authenticate and receive JWT tokens.
```typescript
interface LoginCredentials {
  email: string;
  password: string;
}
interface LoginResponse {
  profile: Profile;
  accessToken: string;
  refreshToken: string;
}
```

### POST /auth/refresh-token
Get a new access token using a refresh token.
```typescript
interface RefreshTokenRequest {
  refreshToken: string;
}
interface TokenPayload {
  accessToken: string;
  refreshToken: string;
}
```

---

## Users

### GET /users
Get all users (admin only).
```typescript
// Response: Profile[]
```

### GET /users/me
Get current user's profile (JWT required).
```typescript
// Response: Profile
```

### GET /users/{id}
Get user by ID (admin or self).
```typescript
// Response: Profile
```

### POST /users
Create a new user (admin only).
```typescript
interface RegisterRequest {
  email: string;
  password: string;
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
}
// Response: Profile
```

### PUT /users/{id}
Update user profile (self or admin).
```typescript
interface ProfileUpdateRequest {
  userId: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
  userRole: string;
  profilePictureUrl?: string;
}
// Response: Profile
```

### DELETE /users/{id}
Delete user (admin only).
```typescript
// Response: { message: string }
```

### POST /users/{id}/profile-picture
Upload profile image (multipart/form-data).

### GET /users/{id}/profile-picture
Get profile image (returns image binary).

---

## Meters

### GET /meters
Get all meters.
```typescript
// Response: Meter[]
```

### GET /meters/{id}
Get meter by ID.
```typescript
// Response: Meter
```

### POST /meters
Create a new meter.
```typescript
interface MeterCreationRequest {
  meterId: string;
  name: string;
  productName?: string;
  description?: string;
  location?: string;
  active: boolean;
}
// Response: { message: string }
```

### PUT /meters/{id}
Update meter.
```typescript
interface MeterCreationRequest { ... }
// Response: { message: string }
```

### DELETE /meters/{id}
Delete meter.
```typescript
// Response: { message: string }
```

---

## Meter User Assignment

### POST /meter-user/assign
Assign a meter to a user.
```typescript
interface MeterUserAssignment {
  meterId: string;
  userId: number;
  isAssigned: boolean;
}
// Response: { success: boolean }
```

### POST /meter-user/unassign
Unassign a meter from a user.
```typescript
interface MeterUserAssignment { ... }
// Response: { success: boolean }
```

### GET /meter-user/user/{userId}/meters
Get meters assigned to a user.
```typescript
// Response: Meter[]
```

### GET /meter-user/meter/{meterId}/users
Get users assigned to a meter.
```typescript
// Response: Profile[]
```

### GET /meter-user/is-assigned?meterId={meterId}&userId={userId}
Check if a meter is assigned to a user.
```typescript
// Response: { isAssigned: boolean }
```

---

## Mpesa Payments

### POST /mpesa/payment
Initiate Mpesa payment.
```typescript
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

### GET /mpesa/status/{checkoutRequestId}
Get Mpesa payment status.
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

### GET /mpesa/transactions
Get all Mpesa transactions.
```typescript
// Response: MpesaTransaction[]
```

---

## Common Interfaces

```typescript
interface Profile {
  userId: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
  userRole: string;
  createdAt: string;
  updatedAt: string;
  profilePictureUrl?: string;
}

interface Meter {
  meterId: string;
  name: string;
  productName?: string;
  description?: string;
  location?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

interface MessageResponse {
  message: string;
}
```

---

## Example Usage (React + Axios)

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

// Add JWT token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Get current user profile
const getCurrentUser = async () => {
  const res = await api.get<Profile>('/users/me');
  return res.data;
};

// Initiate Mpesa payment
const payMpesa = async (data: PaymentRequest) => {
  const res = await api.post<PaymentResponse>('/mpesa/payment', data);
  return res.data;
};
```

---

## Error Handling
All endpoints may return:
- 200/201: Success
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 500: Internal Server Error

Error responses:
```typescript
interface MessageResponse {
  message: string;
}
```

