#!/bin/bash
# Script to test AuthRoutes endpoints using curl
# Make sure your Ktor server is running before executing this script

API_URL="http://localhost:8080/auth"
EMAIL="testuser@example.com"   # Change to a valid test user
PASSWORD="testpassword"        # Change to the correct password
NEW_PASSWORD="newpassword123"  # For change-password test

# 1. Login
echo "\n== LOGIN =="
LOGIN_RESPONSE=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"'$EMAIL'","password":"'$PASSWORD'"}')
echo "$LOGIN_RESPONSE"

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r .accessToken)
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r .refreshToken)
USER_ID=$(echo $LOGIN_RESPONSE | jq -r .profile.id)

# 2. Refresh Token
echo "\n== REFRESH TOKEN =="
REFRESH_RESPONSE=$(curl -s -X POST "$API_URL/refresh-token" \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"'$REFRESH_TOKEN'"}')
echo "$REFRESH_RESPONSE"

NEW_ACCESS_TOKEN=$(echo $REFRESH_RESPONSE | jq -r .accessToken)
NEW_REFRESH_TOKEN=$(echo $REFRESH_RESPONSE | jq -r .refreshToken)

# 3. Verify Token
echo "\n== VERIFY TOKEN =="
VERIFY_RESPONSE=$(curl -s -X POST "$API_URL/verify-token" \
    -H "Content-Type: application/json" \
    -d '{"token":"'$REFRESH_TOKEN'"}')
echo "$VERIFY_RESPONSE"

# 4. Revoke Token
echo "\n== REVOKE TOKEN =="
REVOKE_RESPONSE=$(curl -s -X POST "$API_URL/revoke-token" \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"'$REFRESH_TOKEN'"}')
echo "$REVOKE_RESPONSE"

# 5. Login again to get fresh tokens for further tests
LOGIN_RESPONSE2=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"'$EMAIL'","password":"'$PASSWORD'"}')
ACCESS_TOKEN2=$(echo $LOGIN_RESPONSE2 | jq -r .accessToken)
REFRESH_TOKEN2=$(echo $LOGIN_RESPONSE2 | jq -r .refreshToken)

# 6. Logout
echo "\n== LOGOUT =="
LOGOUT_RESPONSE=$(curl -s -X POST "$API_URL/logout" \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"'$REFRESH_TOKEN2'"}')
echo "$LOGOUT_RESPONSE"

# 7. Login again for JWT-protected endpoints
LOGIN_RESPONSE3=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"'$EMAIL'","password":"'$PASSWORD'"}')
ACCESS_TOKEN3=$(echo $LOGIN_RESPONSE3 | jq -r .accessToken)

# 8. Revoke all user tokens (JWT required)
echo "\n== REVOKE ALL USER TOKENS =="
REVOKE_ALL_RESPONSE=$(curl -s -X DELETE "$API_URL/revoke-user-tokens" \
    -H "Authorization: Bearer $ACCESS_TOKEN3" \
    -H "Content-Type: application/json")
echo "$REVOKE_ALL_RESPONSE"

# 9. Login again for change password
echo "\n== LOGIN FOR CHANGE PASSWORD =="
LOGIN_RESPONSE4=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"'$EMAIL'","password":"'$PASSWORD'"}')
ACCESS_TOKEN4=$(echo $LOGIN_RESPONSE4 | jq -r .accessToken)

# 10. Change Password (JWT required)
echo "\n== CHANGE PASSWORD =="
CHANGE_PASSWORD_RESPONSE=$(curl -s -X POST "$API_URL/change-password" \
    -H "Authorization: Bearer $ACCESS_TOKEN4" \
    -H "Content-Type: application/json" \
    -d '{"newPassword":"'$NEW_PASSWORD'"}')
echo "$CHANGE_PASSWORD_RESPONSE"

# Optionally, change password back to original for repeatable tests
echo "\n== RESET PASSWORD TO ORIGINAL =="
LOGIN_RESPONSE5=$(curl -s -X POST "$API_URL/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"'$EMAIL'","password":"'$NEW_PASSWORD'"}')
ACCESS_TOKEN5=$(echo $LOGIN_RESPONSE5 | jq -r .accessToken)
RESET_PASSWORD_RESPONSE=$(curl -s -X POST "$API_URL/change-password" \
    -H "Authorization: Bearer $ACCESS_TOKEN5" \
    -H "Content-Type: application/json" \
    -d '{"newPassword":"'$PASSWORD'"}')
echo "$RESET_PASSWORD_RESPONSE"

echo "\n== DONE =="

