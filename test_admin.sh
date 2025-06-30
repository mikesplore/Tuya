#!/bin/bash

# Create admin user and test endpoints
BASE_URL="http://localhost:8080"

echo "Creating admin user..."
RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin_test@example.com","password":"admin123","firstName":"Admin","lastName":"Test"}')
  
echo "Response: $RESPONSE"

echo "Logging in as the new user..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"mikepremium8@gmail.com","password":"admin1M23"}')

echo "Login response: $LOGIN_RESPONSE"

# Extract token and user ID
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
USER_ID=$(echo $LOGIN_RESPONSE | jq -r '.userId')

echo "Token: ${TOKEN:0:15}..."
echo "User ID: $USER_ID"

# Create test device
TEST_DEVICE_ID="tuya_test_$(date +%s)"
echo "Creating test device with ID: $TEST_DEVICE_ID"
DEVICE_RESPONSE=$(curl -s -X POST "$BASE_URL/devices" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"deviceId":"'"$TEST_DEVICE_ID"'","name":"Test Device","description":"Test device created by script"}')

echo "Device creation response: $DEVICE_RESPONSE"

# Test GET /devices endpoint
echo "Testing GET /devices endpoint..."
curl -s -X GET "$BASE_URL/devices" \
  -H "Authorization: Bearer $TOKEN" | jq

echo "Done!"
