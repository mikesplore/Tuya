#!/bin/bash

# Set JWT token (needs to be updated)
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJmZjkxOWVlNC0yZTc1LTRhNDctYmEwMy01YmY5ZDRlMzlmYjQiLCJpYXQiOjE3NTEzMDY3NDMsImlzcyI6InR1eWEtYXBwIiwiYXVkIjoidHV5YS11c2VycyIsImV4cCI6MTc1MTMxMDM0MywiZW1haWwiOiJ0ZXN0X2FkbWluQGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIifQ.2TTJysJJ63uoDMjPoxgel43ERcIiKQkUXq2YNdpb8mc"

# Base URL
BASE_URL="http://localhost:8080"

echo "Testing GET /devices endpoint..."
curl -s -X GET "$BASE_URL/devices" \
    -H "Authorization: Bearer $TOKEN" | jq

echo "Testing POST /devices/refresh endpoint..."
curl -s -X POST "$BASE_URL/devices/refresh" \
    -H "Authorization: Bearer $TOKEN" | jq

# Generate test device ID
TEST_DEVICE_ID="tuya_test_$(date +%s)"
echo "Testing POST /devices endpoint with device ID: $TEST_DEVICE_ID"
curl -s -X POST "$BASE_URL/devices" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"deviceId":"'"$TEST_DEVICE_ID"'","name":"Test Device","description":"Test device created by script"}' | jq

echo "Done"
