#!/bin/bash

# This script tests the M-Pesa STK push payment flow with real user interaction
# It initiates a payment and waits for the actual M-Pesa callback

# Base URL for the API
BASE_URL="https://7b9b-41-89-128-6.ngrok-free.app"
EMAIL="mikepremium8@gmail.com"
PASSWORD="Mick@1132"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# Function to print waiting messages
print_wait() {
    echo -e "${BLUE}⏳ $1${NC}"
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

# Ask for phone number to send STK push
echo -e "${YELLOW}Enter the phone number to send STK push (format: 254XXXXXXXXX):${NC}"
read PHONE_NUMBER

# Ask for amount to pay
echo -e "${YELLOW}Enter amount to pay (minimum 1):${NC}"
read AMOUNT

# Step 2: Initiate the payment with real phone number
print_info "Initiating M-Pesa STK push payment to $PHONE_NUMBER for Ksh $AMOUNT..."

# Try up to 3 times to initiate payment if network issues occur
MAX_ATTEMPTS=3
ATTEMPT=0
SUCCESS=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ] && [ "$SUCCESS" = "false" ]; do
    ATTEMPT=$((ATTEMPT + 1))
    
    if [ $ATTEMPT -gt 1 ]; then
        print_info "Retry attempt $ATTEMPT of $MAX_ATTEMPTS..."
        sleep 2
    fi
    
    PAY_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/pay" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"amount\": $AMOUNT,
            \"phoneNumber\": \"$PHONE_NUMBER\",
            \"meterId\": \"08ac8080-d8a7-4407-8d2a-7827eac88796\",
            \"description\": \"Meter top-up payment\"
        }")
        
    # Check if the request succeeded
    if [ $? -eq 0 ] && [[ "$PAY_RESPONSE" == *"\"success\":true"* ]]; then
        SUCCESS=true
        print_result 0 "Payment initiation" "$PAY_RESPONSE"
    else
        print_result 1 "Payment initiation attempt $ATTEMPT" "$PAY_RESPONSE"
    fi
done

if [ "$SUCCESS" = "false" ]; then
    echo -e "${RED}Failed to initiate payment after $MAX_ATTEMPTS attempts. Exiting.${NC}"
    exit 1
fi

# Extract checkoutRequestId and merchantRequestId from payment response
CHECKOUT_REQUEST_ID=$(echo "$PAY_RESPONSE" | grep -o '"checkoutRequestId":"[^"]*' | grep -o '[^"]*$')
MERCHANT_REQUEST_ID=$(echo "$PAY_RESPONSE" | grep -o '"merchantRequestId":"[^"]*' | grep -o '[^"]*$')
TRANSACTION_ID=$(echo "$PAY_RESPONSE" | grep -o '"mpesaTransactionId":"[^"]*' | grep -o '[^"]*$')

if [ -z "$CHECKOUT_REQUEST_ID" ]; then
    echo -e "${RED}Failed to extract checkout request ID from payment response${NC}"
    exit 1
fi

print_info "Extracted checkout request ID: $CHECKOUT_REQUEST_ID"
print_info "Extracted merchant request ID: $MERCHANT_REQUEST_ID"
print_info "Transaction ID: $TRANSACTION_ID"

# Step 3: Check initial transaction status
print_info "Checking initial transaction status..."
STATUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/mpesa/status/${CHECKOUT_REQUEST_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

print_result $? "Initial transaction status check" "$STATUS_RESPONSE"

# Step 4: Instruct user to check their phone
print_wait "An STK push has been sent to your phone ($PHONE_NUMBER)"
print_wait "Please complete the payment on your phone or cancel it"
print_wait "This script will poll the server for updates..."

# Step 5: Poll for status changes and allow manual verification
MAX_POLLS=30  # Maximum number of times to check status
POLL_INTERVAL=5  # Seconds between polls
CURRENT_POLL=0

STATUS="PENDING"
PREV_STATUS="PENDING"

echo -e "${YELLOW}M-Pesa STK push has been sent to your phone. You have three options:${NC}"
echo -e "1. ${GREEN}Complete the payment on your phone${NC}"
echo -e "2. ${RED}Cancel the payment on your phone${NC}"
echo -e "3. ${BLUE}Press 'v' at any time to manually verify the payment${NC}"
echo -e "4. ${YELLOW}Press 'q' to quit${NC}"

# Start polling in the background
while [ $CURRENT_POLL -lt $MAX_POLLS ]; do
    CURRENT_POLL=$((CURRENT_POLL + 1))
    
    # Check if user wants to manually verify
    read -t 1 -n 1 USER_INPUT
    if [[ "$USER_INPUT" == "v" || "$USER_INPUT" == "V" ]]; then
        echo -e "${BLUE}Manually verifying payment...${NC}"
        
        VERIFY_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/verify-payment/${CHECKOUT_REQUEST_ID}" \
            -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/json")
            
        echo -e "${GREEN}Verification response:${NC}"
        echo "$VERIFY_RESPONSE" | jq '.' 2>/dev/null || echo "$VERIFY_RESPONSE"
        
        # Extract status from verification response
        VERIFIED_STATUS=$(echo "$VERIFY_RESPONSE" | grep -o '"status":"[^"]*' | grep -o '[^"]*$')
        VERIFIED_SUCCESS=$(echo "$VERIFY_RESPONSE" | grep -o '"success":[^,}]*' | grep -o '[^:]*$')
        
        if [ "$VERIFIED_STATUS" == "SUCCESS" ] || [ "$VERIFIED_SUCCESS" == "true" ]; then
            echo -e "${GREEN}Payment verified successfully!${NC}"
            break
        elif [ "$VERIFIED_STATUS" == "FAILED" ]; then
            echo -e "${RED}Payment verification confirmed the payment failed.${NC}"
            break
        else
            echo -e "${YELLOW}Payment is still pending or could not be verified.${NC}"
            echo -e "${YELLOW}Continue waiting? (y/n)${NC}"
            read -n 1 CONTINUE
            if [[ "$CONTINUE" != "y" && "$CONTINUE" != "Y" ]]; then
                break
            fi
        fi
    elif [[ "$USER_INPUT" == "q" || "$USER_INPUT" == "Q" ]]; then
        echo -e "${YELLOW}Quitting...${NC}"
        break
    fi
    
    echo -e "${BLUE}⏳ Checking transaction status (attempt $CURRENT_POLL of $MAX_POLLS)...${NC}"
    
    STATUS_RESPONSE=$(curl -s -X GET "${BASE_URL}/mpesa/status/${CHECKOUT_REQUEST_ID}" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json")
    
    STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*' | grep -o '[^"]*$')
    CALLBACK_RECEIVED=$(echo "$STATUS_RESPONSE" | grep -o '"callbackReceived":[^,}]*' | grep -o '[^:]*$')
    RESULT_CODE=$(echo "$STATUS_RESPONSE" | grep -o '"responseCode":"[^"]*' | grep -o '[^"]*$')
    RESULT_DESC=$(echo "$STATUS_RESPONSE" | grep -o '"responseDescription":"[^"]*' | grep -o '[^"]*$')
    
    if [ "$STATUS" != "$PREV_STATUS" ] || [ "$CALLBACK_RECEIVED" = "true" ]; then
        echo -e "${GREEN}Status changed to: $STATUS${NC}"
        echo -e "${GREEN}Callback received: $CALLBACK_RECEIVED${NC}"
        echo -e "${GREEN}Result code: $RESULT_CODE${NC}"
        echo -e "${GREEN}Description: $RESULT_DESC${NC}"
        
        if [ "$STATUS" = "SUCCESS" ]; then
            echo -e "${GREEN}Transaction completed successfully!${NC}"
            
            # Get receipt number if available
            RECEIPT=$(echo "$STATUS_RESPONSE" | grep -o '"mpesaReceiptNumber":"[^"]*' | grep -o '[^"]*$')
            if [ ! -z "$RECEIPT" ]; then
                echo -e "${GREEN}M-Pesa Receipt Number: $RECEIPT${NC}"
            fi
            
            break
        elif [ "$STATUS" = "FAILED" ]; then
            echo -e "${RED}Transaction failed with code $RESULT_CODE: $RESULT_DESC${NC}"
            break
        fi
        
        PREV_STATUS="$STATUS"
    else
        echo -e "${BLUE}Status unchanged: $STATUS${NC}"
    fi
    
    # If we've reached maximum polls and no callback
    if [ $CURRENT_POLL -eq $MAX_POLLS ]; then
        echo -e "${RED}Maximum polling attempts reached. Final status: $STATUS${NC}"
        echo -e "${YELLOW}Would you like to manually verify the payment? (y/n)${NC}"
        read -n 1 VERIFY
        if [[ "$VERIFY" == "y" || "$VERIFY" == "Y" ]]; then
            echo -e "${BLUE}Manually verifying payment...${NC}"
            
            VERIFY_RESPONSE=$(curl -s -X POST "${BASE_URL}/mpesa/verify-payment/${CHECKOUT_REQUEST_ID}" \
                -H "Authorization: Bearer $TOKEN" \
                -H "Content-Type: application/json")
                
            echo -e "${GREEN}Verification response:${NC}"
            echo "$VERIFY_RESPONSE" | jq '.' 2>/dev/null || echo "$VERIFY_RESPONSE"
        fi
        break
    fi
    
    sleep $POLL_INTERVAL
done

# Step 6: Show final transaction details
print_info "Final transaction details:"
curl -s -X GET "${BASE_URL}/mpesa/status/${CHECKOUT_REQUEST_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" | jq '.'

print_info "Test completed! You can verify this payment in your payment history."

# Optionally show payment history
echo -e "${YELLOW}Do you want to see your payment history? (y/n)${NC}"
read VIEW_HISTORY

if [[ "$VIEW_HISTORY" == "y" || "$VIEW_HISTORY" == "Y" ]]; then
    print_info "Retrieving payment history..."
    curl -s -X GET "${BASE_URL}/mpesa/payments?limit=5" \
        -H "Authorization: Bearer $TOKEN" | jq '.'
fi

echo -e "${GREEN}Test completed!${NC}"
