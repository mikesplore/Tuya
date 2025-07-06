#!/bin/bash

# Base URL for the API
BASE_URL="https://68e7-41-89-128-6.ngrok-free.app"
EMAIL="mikepremium8@gmail.com"
PASSWORD="Mick@1132"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print success/failure messages
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
        echo "Response: $3"
    else
        echo -e "${RED}✗ $2${NC}"
        echo "Error Response: $3"
    fi
}

# Function to print info messages
print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Step 1: Get authentication token
print_info "Getting authentication token..."
AUTH_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")

TOKEN=$(echo "$AUTH_RESPONSE" | grep -o '"token":"[^"]*' | grep -o '[^"]*$')

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Failed to get authentication token${NC}"
    echo "Response: $AUTH_RESPONSE"
    exit 1
fi

echo -e "${GREEN}Successfully got authentication token${NC}"

# Step 2: Test initiating payment
print_info "Testing payment initiation..."
PAY_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/pay" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "amount": 1,
        "phoneNumber": "254799013845",
        "meterId": "08ac8080-d8a7-4407-8d2a-7827eac88796",
        "description": "Test meter top-up payment"
    }')

print_result $? "Payment initiation" "$PAY_RESPONSE"

# Extract checkoutRequestId and merchantRequestId from payment response
CHECKOUT_REQUEST_ID=$(echo "$PAY_RESPONSE" | grep -o '"checkoutRequestId":"[^"]*' | grep -o '[^"]*$')
MERCHANT_REQUEST_ID=$(echo "$PAY_RESPONSE" | grep -o '"merchantRequestId":"[^"]*' | grep -o '[^"]*$')

if [ ! -z "$CHECKOUT_REQUEST_ID" ]; then
    print_info "Extracted checkout request ID: $CHECKOUT_REQUEST_ID"
    print_info "Extracted merchant request ID: $MERCHANT_REQUEST_ID"

    print_info "Checking initial transaction status..."
    STATUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/mpesa/status/${CHECKOUT_REQUEST_ID}" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json")

    print_result $? "Initial transaction status check" "$STATUS_RESPONSE"
else
    echo -e "${RED}Failed to extract checkout request ID from payment response${NC}"
    exit 1
fi

# Step 3: Test callback endpoint with proper M-Pesa format
print_info "Testing callback endpoint with realistic M-Pesa payload..."

# Create a more realistic callback payload
CALLBACK_PAYLOAD=$(cat <<EOF
{
    "Body": {
        "stkCallback": {
            "MerchantRequestID": "${MERCHANT_REQUEST_ID}",
            "CheckoutRequestID": "${CHECKOUT_REQUEST_ID}",
            "ResultCode": 0,
            "ResultDesc": "The service request is processed successfully.",
            "CallbackMetadata": {
                "Item": [
                    {
                        "Name": "Amount",
                        "Value": 1.00
                    },
                    {
                        "Name": "MpesaReceiptNumber",
                        "Value": "TEST${RANDOM}"
                    },
                    {
                        "Name": "TransactionDate",
                        "Value": $(date +%Y%m%d%H%M%S)
                    },
                    {
                        "Name": "PhoneNumber",
                        "Value": 254799013845
                    }
                ]
            }
        }
    }
}
EOF
)

print_info "Callback payload:"
echo "$CALLBACK_PAYLOAD" | jq '.' 2>/dev/null || echo "$CALLBACK_PAYLOAD"

CALLBACK_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/callback" \
    -H "Content-Type: application/json" \
    -d "$CALLBACK_PAYLOAD")

print_result $? "Callback endpoint test" "$CALLBACK_RESPONSE"

# Step 4: Verify payment status has been updated after callback
print_info "Waiting 3 seconds for callback processing..."
sleep 3

print_info "Verifying payment status after callback..."
UPDATED_STATUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/mpesa/status/${CHECKOUT_REQUEST_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

print_result $? "Updated transaction status check" "$UPDATED_STATUS_RESPONSE"

# Step 5: Test getting payment history
print_info "Testing payment history retrieval..."
HISTORY_RESPONSE=$(curl -s -X GET "${BASE_URL}/mpesa/payments?limit=5" \
    -H "Authorization: Bearer $TOKEN")

print_result $? "Payment history retrieval" "$HISTORY_RESPONSE"

# Step 6: Test failed payment callback (user cancellation)
print_info "Testing failed payment callback (user cancellation)..."
FAILED_CALLBACK_PAYLOAD=$(cat <<EOF
{
    "Body": {
        "stkCallback": {
            "MerchantRequestID": "${MERCHANT_REQUEST_ID}",
            "CheckoutRequestID": "${CHECKOUT_REQUEST_ID}",
            "ResultCode": 1032,
            "ResultDesc": "Request cancelled by user"
        }
    }
}
EOF
)

print_info "Failed callback payload:"
echo "$FAILED_CALLBACK_PAYLOAD" | jq '.' 2>/dev/null || echo "$FAILED_CALLBACK_PAYLOAD"

FAILED_CALLBACK_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/callback" \
    -H "Content-Type: application/json" \
    -d "$FAILED_CALLBACK_PAYLOAD")

print_result $? "Failed payment callback test" "$FAILED_CALLBACK_RESPONSE"

# Step 7: Verify payment status shows failed after cancellation callback
print_info "Waiting 2 seconds for failed callback processing..."
sleep 2

print_info "Verifying payment status after cancellation..."
FAILED_STATUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/mpesa/status/${CHECKOUT_REQUEST_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

print_result $? "Failed transaction status check" "$FAILED_STATUS_RESPONSE"

# Step 8: Test another common error scenario - insufficient funds
print_info "Testing insufficient funds error callback..."
INSUFFICIENT_FUNDS_PAYLOAD=$(cat <<EOF
{
    "Body": {
        "stkCallback": {
            "MerchantRequestID": "${MERCHANT_REQUEST_ID}",
            "CheckoutRequestID": "${CHECKOUT_REQUEST_ID}",
            "ResultCode": 1002,
            "ResultDesc": "The balance is insufficient for the transaction."
        }
    }
}
EOF
)

print_info "Insufficient funds callback payload:"
echo "$INSUFFICIENT_FUNDS_PAYLOAD" | jq '.' 2>/dev/null || echo "$INSUFFICIENT_FUNDS_PAYLOAD"

INSUFFICIENT_FUNDS_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/callback" \
    -H "Content-Type: application/json" \
    -d "$INSUFFICIENT_FUNDS_PAYLOAD")

print_result $? "Insufficient funds callback test" "$INSUFFICIENT_FUNDS_RESPONSE"

# Step 9: Test a malformed callback payload to check error handling
print_info "Testing malformed callback payload..."
MALFORMED_PAYLOAD=$(cat <<EOF
{
    "Body": {
        "stkCallback": {
            "MerchantRequestID": "${MERCHANT_REQUEST_ID}",
            "ResultCode": "invalid-string-instead-of-number",
            "ResultDesc": "Malformed test data"
        }
    }
}
EOF
)

MALFORMED_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/callback" \
    -H "Content-Type: application/json" \
    -d "$MALFORMED_PAYLOAD")

print_result $? "Malformed payload test" "$MALFORMED_RESPONSE"

# Step 10: Test manual payment verification endpoint
print_info "Testing manual payment verification..."

VERIFICATION_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/verify-payment/${CHECKOUT_REQUEST_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

print_result $? "Manual payment verification" "$VERIFICATION_RESPONSE"

print_info "All tests completed!"