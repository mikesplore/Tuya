#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URL for the API
BASE_URL="http://localhost:8080"

# Admin credentials
ADMIN_EMAIL="mikepremium8@gmail.com"
ADMIN_PASSWORD="Mick@1132"

# Test results tracking
PASSED=0
FAILED=0
TOTAL=0

# --------------------------------------
# Helper functions
# --------------------------------------

log_info() {
    echo -e "${BLUE}INFO:${NC} $1"
}

log_success() {
    echo -e "${GREEN}SUCCESS:${NC} $1"
    PASSED=$((PASSED+1))
    TOTAL=$((TOTAL+1))
}

log_error() {
    echo -e "${RED}ERROR:${NC} $1"
    FAILED=$((FAILED+1))
    TOTAL=$((TOTAL+1))
}

log_warning() {
    echo -e "${YELLOW}WARNING:${NC} $1"
}

# Check if server is running
check_server() {
    curl -s "$BASE_URL" > /dev/null
    if [ $? -ne 0 ]; then
        log_error "Server is not running at $BASE_URL"
        exit 1
    else
        log_info "Server is running at $BASE_URL"
    fi
}

# Get admin JWT token
get_admin_token() {
    log_info "Getting admin JWT token..."
    TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"email":"'"$ADMIN_EMAIL"'","password":"'"$ADMIN_PASSWORD"'"}' | jq -r '.token')

    if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
        log_error "Failed to get admin token"
        exit 1
    else
        log_success "Got admin token: ${TOKEN:0:15}..."
    fi
}

# --------------------------------------
# Device API Tests
# --------------------------------------

test_get_all_devices() {
    log_info "Testing GET /devices endpoint..."
    RESPONSE=$(curl -s -X GET "$BASE_URL/devices" \
        -H "Authorization: Bearer $TOKEN")
    
    if [[ $(echo "$RESPONSE" | jq -e '.devices') ]]; then
        DEVICE_COUNT=$(echo "$RESPONSE" | jq '.count')
        log_success "Successfully retrieved $DEVICE_COUNT devices"
        # Save first device ID for later tests if available
        if [[ "$DEVICE_COUNT" -gt 0 ]]; then
            DEVICE_ID=$(echo "$RESPONSE" | jq -r '.devices[0].id')
            log_info "Using device ID: $DEVICE_ID for further tests"
        fi
    else
        log_error "Failed to get devices: $RESPONSE"
    fi
}

test_refresh_devices() {
    log_info "Testing POST /devices/refresh endpoint..."
    RESPONSE=$(curl -s -X POST "$BASE_URL/devices/refresh" \
        -H "Authorization: Bearer $TOKEN")
    
    if [[ $(echo "$RESPONSE" | jq -e '.message') ]]; then
        TOTAL=$(echo "$RESPONSE" | jq '.total')
        NEW=$(echo "$RESPONSE" | jq '.new')
        UPDATED=$(echo "$RESPONSE" | jq '.updated')
        log_success "Successfully refreshed devices: Total $TOTAL, New $NEW, Updated $UPDATED"
    else
        log_error "Failed to refresh devices: $RESPONSE"
    fi
}

test_get_device_by_id() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping GET /devices/{id} test: No device ID available"
        return
    fi
    
    log_info "Testing GET /devices/$DEVICE_ID endpoint..."
    RESPONSE=$(curl -s -X GET "$BASE_URL/devices/$DEVICE_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if [[ $(echo "$RESPONSE" | jq -e '.id') || $(echo "$RESPONSE" | jq -e '.device') ]]; then
        log_success "Successfully retrieved device details for ID: $DEVICE_ID"
    else
        log_error "Failed to get device details: $RESPONSE"
    fi
}

test_create_device() {
    # Generate a random device ID for testing
    TEST_DEVICE_ID="tuya_test_$(date +%s)"
    
    log_info "Testing POST /devices endpoint with device ID: $TEST_DEVICE_ID"
    RESPONSE=$(curl -s -X POST "$BASE_URL/devices" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{"deviceId":"'"$TEST_DEVICE_ID"'","name":"Test Device","description":"Test device created by script"}')
    
    if [[ $(echo "$RESPONSE" | jq -e '.id') ]]; then
        NEW_DEVICE_ID=$(echo "$RESPONSE" | jq -r '.id')
        log_success "Successfully created device with ID: $NEW_DEVICE_ID"
        # Save for later tests
        if [[ -z "$DEVICE_ID" ]]; then
            DEVICE_ID=$NEW_DEVICE_ID
            log_info "Using newly created device ID: $DEVICE_ID for further tests"
        fi
    elif [[ $(echo "$RESPONSE" | jq -e '.error') == "device_not_found" ]]; then
        log_warning "Device creation skipped: Device ID not found in Tuya Cloud. This is expected in test environment."
        # For testing purposes, try to use an existing device ID
        if [[ -z "$DEVICE_ID" ]]; then
            RESPONSE=$(curl -s -X GET "$BASE_URL/devices" \
                -H "Authorization: Bearer $TOKEN")
            if [[ $(echo "$RESPONSE" | jq -e '.devices[0].id') ]]; then
                DEVICE_ID=$(echo "$RESPONSE" | jq -r '.devices[0].id')
                log_info "Using existing device ID: $DEVICE_ID for further tests"
            fi
        fi
    else
        log_error "Failed to create device: $RESPONSE"
        # Dump the full response for debugging
        echo "$RESPONSE" | jq '.'
    fi
}

test_update_device() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping PUT /devices/{id} test: No device ID available"
        return
    fi
    
    log_info "Testing PUT /devices/$DEVICE_ID endpoint..."
    RESPONSE=$(curl -s -X PUT "$BASE_URL/devices/$DEVICE_ID" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{"name":"Updated Device Name","description":"Updated by test script","location":"Test Location"}')
    
    if [[ $(echo "$RESPONSE" | jq -e '.id') ]]; then
        log_success "Successfully updated device with ID: $DEVICE_ID"
    else
        log_error "Failed to update device: $RESPONSE"
    fi
}

test_send_command() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping POST /devices/{id}/commands test: No device ID available"
        return
    fi
    
    log_info "Testing POST /devices/$DEVICE_ID/commands endpoint..."
    RESPONSE=$(curl -s -X POST "$BASE_URL/devices/$DEVICE_ID/commands" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d "{\"code\":\"switch_1\",\"value\":{\"type\":\"boolean\",\"value\":true}}")
    
    if [[ $(echo "$RESPONSE" | jq -e '.success') ]]; then
        log_success "Successfully sent command to device with ID: $DEVICE_ID"
    else
        log_error "Failed to send command: $RESPONSE"
    fi
}

test_add_balance() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping POST /devices/{id}/add-balance test: No device ID available"
        return
    fi
    
    log_info "Testing POST /devices/$DEVICE_ID/add-balance endpoint..."
    RESPONSE=$(curl -s -X POST "$BASE_URL/devices/$DEVICE_ID/add-balance" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{"amount":50.0}')
    
    if [[ $(echo "$RESPONSE" | jq -e '.success') ]]; then
        log_success "Successfully added balance to device with ID: $DEVICE_ID"
    else
        log_error "Failed to add balance: $RESPONSE"
    fi
}

test_get_device_users() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping GET /devices/{id}/users test: No device ID available"
        return
    fi
    
    log_info "Testing GET /devices/$DEVICE_ID/users endpoint..."
    RESPONSE=$(curl -s -X GET "$BASE_URL/devices/$DEVICE_ID/users" \
        -H "Authorization: Bearer $TOKEN")
    
    if [[ $(echo "$RESPONSE" | jq -e '.') ]]; then
        USER_COUNT=$(echo "$RESPONSE" | jq '. | length')
        log_success "Successfully retrieved $USER_COUNT users assigned to device ID: $DEVICE_ID"
    else
        log_error "Failed to get assigned users: $RESPONSE"
    fi
}

test_assign_user_to_device() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping POST /devices/{id}/assign test: No device ID available"
        return
    fi
    
    # For this test, we need a user ID. If you don't have a specific test user,
    # you can create one or get one from the users list
    TEST_USER_ID="test_user_1"  # Replace with an actual user ID
    
    log_info "Testing POST /devices/$DEVICE_ID/assign endpoint with user ID: $TEST_USER_ID"
    RESPONSE=$(curl -s -X POST "$BASE_URL/devices/$DEVICE_ID/assign" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{"userId":"'"$TEST_USER_ID"'"}')
    
    if [[ $(echo "$RESPONSE" | jq -e '.id') ]]; then
        log_success "Successfully assigned user $TEST_USER_ID to device $DEVICE_ID"
        ASSIGNMENT_ID=$(echo "$RESPONSE" | jq -r '.id')
    else
        log_error "Failed to assign user to device: $RESPONSE"
    fi
}

test_unassign_user_from_device() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping DELETE /devices/{id}/assign/{userId} test: No device ID available"
        return
    fi
    
    # Use the same test user ID as in the assignment test
    TEST_USER_ID="test_user_1"  # Replace with an actual user ID
    
    log_info "Testing DELETE /devices/$DEVICE_ID/assign/$TEST_USER_ID endpoint..."
    RESPONSE=$(curl -s -X DELETE "$BASE_URL/devices/$DEVICE_ID/assign/$TEST_USER_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if [[ $(echo "$RESPONSE" | jq -e '.message') ]]; then
        log_success "Successfully unassigned user $TEST_USER_ID from device $DEVICE_ID"
    else
        log_error "Failed to unassign user from device: $RESPONSE"
    fi
}

test_delete_device() {
    if [[ -z "$DEVICE_ID" ]]; then
        log_warning "Skipping DELETE /devices/{id} test: No device ID available"
        return
    fi
    
    log_info "Testing DELETE /devices/$DEVICE_ID endpoint..."
    RESPONSE=$(curl -s -X DELETE "$BASE_URL/devices/$DEVICE_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if [[ $(echo "$RESPONSE" | jq -e '.message') ]]; then
        log_success "Successfully deleted device with ID: $DEVICE_ID"
    else
        log_error "Failed to delete device: $RESPONSE"
    fi
}

# --------------------------------------
# Run tests
# --------------------------------------

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo "Error: jq is not installed. Please install it first."
    echo "On Ubuntu/Debian: sudo apt-get install jq"
    echo "On macOS: brew install jq"
    exit 1
fi

# Initialize
check_server
get_admin_token

# Run device API tests
echo -e "\n${BLUE}=== Running Device API Tests ===${NC}\n"

# GET requests first to avoid modifying data
test_get_all_devices
test_get_device_by_id
test_get_device_users

# Refresh devices from cloud
test_refresh_devices

# POST, PUT operations
test_create_device
test_update_device
test_send_command
test_add_balance

# User assignment operations
test_assign_user_to_device
test_unassign_user_from_device

# DELETE operation (only if you want to actually delete the device)
# Uncomment to enable
# test_delete_device

# Print summary
echo -e "\n${BLUE}=== Test Summary ===${NC}"
echo -e "Total tests: $TOTAL"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo -e "\n${GREEN}All tests passed successfully!${NC}"
    exit 0
else
    echo -e "\n${RED}Some tests failed. Please check the logs above.${NC}"
    exit 1
fi