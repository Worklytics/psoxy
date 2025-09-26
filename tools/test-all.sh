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


# Extract API connector instances and run tests
API_CONNECTORS=$(echo "$TERRAFORM_OUTPUT" | jq -r '.api_connector_instances.value | keys[]' 2>/dev/null)
if [ $? -eq 0 ] && [ -n "$API_CONNECTORS" ]; then
    while IFS= read -r connector; do
        ./test-${connector}.sh
        LAST_EXIT_CODE=$?
        OVERALL_EXIT_CODE=$((OVERALL_EXIT_CODE || LAST_EXIT_CODE))
        if [ $LAST_EXIT_CODE -ne 0 ]; then
            FAILED_TESTS+=("$connector")
        fi
    done <<< "$API_CONNECTORS"
fi


# Extract bulk connector instances and run tests
BULK_CONNECTORS=$(echo "$TERRAFORM_OUTPUT" | jq -r '.bulk_connector_instances.value | keys[]' 2>/dev/null)
if [ $? -eq 0 ] && [ -n "$BULK_CONNECTORS" ]; then
    while IFS= read -r connector; do
        ./test-${connector}.sh
        LAST_EXIT_CODE=$?
        OVERALL_EXIT_CODE=$((OVERALL_EXIT_CODE || LAST_EXIT_CODE))
        if [ $LAST_EXIT_CODE -ne 0 ]; then
            FAILED_TESTS+=("$connector")
        fi
    done <<< "$BULK_CONNECTORS"
fi


# Extract webhook collector instances and run tests
WEBHOOK_COLLECTORS=$(echo "$TERRAFORM_OUTPUT" | jq -r '.webhook_collector_instances.value | keys[]' 2>/dev/null)
if [ $? -eq 0 ] && [ -n "$WEBHOOK_COLLECTORS" ]; then
    while IFS= read -r connector; do
        ./test-${connector}.sh
        LAST_EXIT_CODE=$?
        OVERALL_EXIT_CODE=$((OVERALL_EXIT_CODE || LAST_EXIT_CODE))
        if [ $LAST_EXIT_CODE -ne 0 ]; then
            FAILED_TESTS+=("$connector")
        fi
    done <<< "$WEBHOOK_COLLECTORS"
fi

# Summary
echo ""
if [ $OVERALL_EXIT_CODE -eq 0 ]; then
    printf "${GREEN}All tests passed successfully!${NC}\n"
else
    printf "${RED}Some tests failed: ${FAILED_TESTS[*]}${NC}\n"
fi

exit $OVERALL_EXIT_CODE

