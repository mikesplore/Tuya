# Authentication API Documentation

## Endpoints

### POST /auth/login
Authenticate user and return access and refresh tokens.

**Request:**
```typescript
interface LoginCredentials {
  email: string;
  password: string;
}
```

**Response:**
```typescript
interface LoginResponse {
  profile: Profile;
  accessToken: string;
  refreshToken: string;
}
```

### POST /auth/refresh-token
Get a new access token using a refresh token.

**Request:**
```typescript
interface RefreshTokenRequest {
  refreshToken: string;
}
```

**Response:**
```typescript
interface TokenPayload {
  accessToken: string;
  refreshToken: string;
}
```

### POST /auth/verify-token
Verify the validity of a token.

**Request:**
```typescript
interface VerifyTokenRequest {
  token: string;
}
```

**Response:**
```typescript
interface VerifyTokenResponse {
  isValid: boolean;
  userId?: number;
}
```

### POST /auth/revoke-token
Revoke a specific refresh token.

**Request:**
```typescript
interface RefreshTokenRequest {
  refreshToken: string;
}
```

**Response:**
```typescript
interface MessageResponse {
  message: string;
}
```

### POST /auth/logout
Revoke the current user's refresh token.

**Request:**
```typescript
interface RefreshTokenRequest {
  refreshToken: string;
}
```

**Response:**
```typescript
interface MessageResponse {
  message: string;
}
```

### DELETE /auth/revoke-user-tokens
Revoke all tokens for the current user (JWT required).

**Response:**
```typescript
interface MessageResponse {
  message: string;
}
```

### POST /auth/change-password
Change the password for the current user (JWT required).

**Request:**
```typescript
interface ChangePasswordRequest {
  newPassword: string;
}
```

**Response:**
```typescript
interface MessageResponse {
  message: string;
}
```

## Error Responses
All endpoints may return error responses:
```typescript
interface ErrorResponse {
  error: string;
}
```

## Security
All endpoints (except /login and /refresh-token) require a Bearer JWT token in the `Authorization` header:
```
Authorization: Bearer <accessToken>
```

