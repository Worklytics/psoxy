#!/bin/bash

# Colors for output
RED='\e[0;31m'
GREEN='\e[0;32m'
YELLOW='\e[0;33m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Check prerequisites
if ! command -v jq &> /dev/null; then
    printf "${RED}Error: jq is not installed. Please install jq to run this script.${NC}\n"
    printf "Install with: brew install jq (macOS) or apt-get install jq (Ubuntu/Debian)\n"
    exit 1
fi

if ! command -v terraform &> /dev/null; then
    printf "${RED}Error: terraform is not installed. Please install terraform to run this script.${NC}\n"
    exit 1
fi

# Function to run a test script and capture its exit code
run_test() {
    local test_script="$1"
    local connector_name="$2"
    
    if [ -f "$test_script" ]; then
        printf "${BLUE}Running test for $connector_name...${NC}\n"
        "$test_script"
        return $?
    else
        printf "${YELLOW}Warning: Test script $test_script not found for $connector_name${NC}\n"
        return 1
    fi
}

# Get terraform output as JSON
echo "Getting terraform output..."
TERRAFORM_OUTPUT=$(terraform output --json)

if [ $? -ne 0 ]; then
    printf "${RED}Error: Failed to get terraform output${NC}\n"
    exit 1
fi

# Initialize exit code tracking
OVERALL_EXIT_CODE=0
FAILED_TESTS=()

echo "Testing API Connectors ..."

# Extract API connector instances and run tests
API_CONNECTORS=$(echo "$TERRAFORM_OUTPUT" | jq -r '.api_connector_instances.value | keys[]' 2>/dev/null)
if [ $? -eq 0 ] && [ -n "$API_CONNECTORS" ]; then
    while IFS= read -r connector; do
        test_script="./test-${connector}.sh"
        run_test "$test_script" "$connector"
        if [ $? -ne 0 ]; then
            OVERALL_EXIT_CODE=1
            FAILED_TESTS+=("$connector")
        fi
    done <<< "$API_CONNECTORS"
else
    printf "${YELLOW}Warning: No API connector instances found in terraform output${NC}\n"
fi

echo ""
echo "Testing Bulk Connectors ..."

# Extract bulk connector instances and run tests
BULK_CONNECTORS=$(echo "$TERRAFORM_OUTPUT" | jq -r '.bulk_connector_instances.value | keys[]' 2>/dev/null)
if [ $? -eq 0 ] && [ -n "$BULK_CONNECTORS" ]; then
    while IFS= read -r connector; do
        test_script="./test-${connector}.sh"
        run_test "$test_script" "$connector"
        if [ $? -ne 0 ]; then
            OVERALL_EXIT_CODE=1
            FAILED_TESTS+=("$connector")
        fi
    done <<< "$BULK_CONNECTORS"
else
    printf "${YELLOW}Warning: No bulk connector instances found in terraform output${NC}\n"
fi

echo ""
echo "Testing Webhook Collectors ..."

# Extract webhook collector instances and run tests
WEBHOOK_COLLECTORS=$(echo "$TERRAFORM_OUTPUT" | jq -r '.webhook_collector_instances.value | keys[]' 2>/dev/null)
if [ $? -eq 0 ] && [ -n "$WEBHOOK_COLLECTORS" ]; then
    while IFS= read -r connector; do
        test_script="./test-${connector}.sh"
        run_test "$test_script" "$connector"
        if [ $? -ne 0 ]; then
            OVERALL_EXIT_CODE=1
            FAILED_TESTS+=("$connector")
        fi
    done <<< "$WEBHOOK_COLLECTORS"
else
    printf "${YELLOW}Warning: No webhook collector instances found in terraform output${NC}\n"
fi

# Summary
echo ""
if [ $OVERALL_EXIT_CODE -eq 0 ]; then
    printf "${GREEN}All tests passed successfully!${NC}\n"
else
    printf "${RED}Some tests failed: ${FAILED_TESTS[*]}${NC}\n"
fi

exit $OVERALL_EXIT_CODE

