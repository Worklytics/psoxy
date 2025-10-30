#!/bin/bash

# Script to replay writes on S3 objects created since a specific timestamp
# (useful to re-trigger bulk processing of those objects via proxy)
# Usage: ./replay-s3-writes.sh <bucket_name> [timestamp]
# Timestamp format: YYYY-MM-DDTHH:MM:SSZ (ISO 8601)
# If timestamp is not provided, defaults to one week ago

set -euo pipefail

# Color definitions
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to display usage information
usage() {
    echo "Usage: $0 [--role ROLE_ARN] <bucket_name> [timestamp]"
    echo "   OR: $0 [--role ROLE_ARN] <object_path>"
    echo ""
    echo "Options:"
    echo "  --role ROLE_ARN  : IAM role ARN to assume before accessing S3"
    echo ""
    echo "Arguments:"
    echo "  bucket_name  : S3 bucket name (with or without s3:// prefix)"
    echo "  timestamp    : ISO 8601 timestamp (YYYY-MM-DDTHH:MM:SSZ) - optional"
    echo "                If not provided, defaults to one week ago"
    echo "  object_path  : S3 object path (with or without s3:// prefix) to replay a single object"
    echo ""
    echo "Examples:"
    echo "  $0 my-bucket                                    # Replay writes on objects from last week"
    echo "  $0 s3://my-bucket                               # Replay writes on objects from last week (s3:// prefix accepted)"
    echo "  $0 my-bucket 2024-01-01T00:00:00Z              # Replay writes on objects since specific date"
    echo "  $0 s3://my-bucket/path/to/object.json          # Replay write on a single object"
    echo "  $0 my-bucket/path/to/object.json               # Replay write on a single object (s3:// added automatically)"
    echo "  $0 --role arn:aws:iam::123456789012:role/MyRole my-bucket  # Assume role before accessing bucket"
    echo ""
    echo "This script will:"
    echo "  • If bucket_name provided: List all objects in the bucket created after the specified timestamp and replay writes"
    echo "  • If object_path provided: Replay write on just that single object using 'aws s3api copy-object'"
    echo ""
    echo "Note: This script adds/updates 'psoxy-last-replay' metadata field to trigger S3 events."
    echo "      Existing metadata, tags, and content are preserved."
    exit 1
}

# Parse --role option if provided
ROLE_ARN=""
if [ $# -ge 1 ] && [ "$1" = "--role" ]; then
    if [ $# -lt 2 ]; then
        echo "Error: --role option requires a role ARN"
        usage
    fi
    ROLE_ARN="$2"
    shift 2
fi

# Check if correct number of arguments provided (after shifting --role if present)
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Error: Invalid number of arguments"
    usage
fi

FIRST_ARG="$1"

# Check if first argument is an object path (contains a slash after the bucket name)
# For s3:// URLs, check if there's a slash after the bucket name
# For non-s3:// URLs, check if there's a slash (indicating bucket/path structure)
if [[ "$FIRST_ARG" == s3://*/* ]] || ([[ "$FIRST_ARG" != s3://* ]] && [[ "$FIRST_ARG" == */* ]]); then
    # Single object mode
    if [ $# -ne 1 ]; then
        echo "Error: When providing an object path, no additional arguments are allowed"
        usage
    fi
    # Parse bucket and key from path
    if [[ "$FIRST_ARG" == s3://* ]]; then
        TEMP_PATH="${FIRST_ARG#s3://}"
    else
        TEMP_PATH="$FIRST_ARG"
    fi
    BUCKET_NAME="${TEMP_PATH%%/*}"
    OBJECT_KEY="${TEMP_PATH#*/}"
    SINGLE_OBJECT_MODE=true
else
    # Bucket mode
    # Remove s3:// prefix if present for bucket name
    if [[ "$FIRST_ARG" == s3://* ]]; then
        BUCKET_NAME="${FIRST_ARG#s3://}"
    else
        BUCKET_NAME="$FIRST_ARG"
    fi
    SINGLE_OBJECT_MODE=false
    
    # Set timestamp - default to one week ago if not provided
    if [ $# -eq 2 ]; then
        TIMESTAMP="$2"
    else
        # Calculate one week ago in ISO 8601 format
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            TIMESTAMP=$(date -u -v-7d '+%Y-%m-%dT%H:%M:%SZ')
        else
            # Linux
            TIMESTAMP=$(date -u -d '1 week ago' '+%Y-%m-%dT%H:%M:%SZ')
        fi
        echo -e "No timestamp provided, defaulting to one week ago: ${BLUE}$TIMESTAMP${NC}"
    fi
    
    # Validate bucket name (basic check)
    if [[ -z "$BUCKET_NAME" ]]; then
        echo "Error: Bucket name cannot be empty"
        usage
    fi
fi

# Validate timestamp format (basic ISO 8601 check) - only in bucket mode
if [[ "$SINGLE_OBJECT_MODE" == false ]] && [[ ! "$TIMESTAMP" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
    echo "Error: Invalid timestamp format. Expected: YYYY-MM-DDTHH:MM:SSZ"
    echo "Example: 2024-01-01T00:00:00Z"
    exit 1
fi

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
    echo "Error: aws command not found. Please install AWS CLI."
    exit 1
fi

# Check if user is authenticated
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}Error: Not authenticated with AWS. Please configure your AWS credentials.${NC}"
    exit 1
fi

# Assume role if provided
if [ -n "$ROLE_ARN" ]; then
    echo -e "Assuming role: ${BLUE}$ROLE_ARN${NC}"
    
    # Generate a session name
    SESSION_NAME="replay-s3-writes-$(date +%s)"
    
    # Assume the role and capture credentials
    ASSUME_ROLE_OUTPUT=$(aws sts assume-role \
        --role-arn "$ROLE_ARN" \
        --role-session-name "$SESSION_NAME" \
        --output json 2>&1)
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Failed to assume role${NC}"
        echo "$ASSUME_ROLE_OUTPUT"
        exit 1
    fi
    
    # Extract credentials and export as environment variables
    if command -v jq &> /dev/null; then
        # Use jq for reliable JSON parsing if available
        export AWS_ACCESS_KEY_ID=$(echo "$ASSUME_ROLE_OUTPUT" | jq -r '.Credentials.AccessKeyId')
        export AWS_SECRET_ACCESS_KEY=$(echo "$ASSUME_ROLE_OUTPUT" | jq -r '.Credentials.SecretAccessKey')
        export AWS_SESSION_TOKEN=$(echo "$ASSUME_ROLE_OUTPUT" | jq -r '.Credentials.SessionToken')
    else
        # Fall back to grep if jq not available
        export AWS_ACCESS_KEY_ID=$(echo "$ASSUME_ROLE_OUTPUT" | grep -o '"AccessKeyId": "[^"]*' | cut -d'"' -f4)
        export AWS_SECRET_ACCESS_KEY=$(echo "$ASSUME_ROLE_OUTPUT" | grep -o '"SecretAccessKey": "[^"]*' | cut -d'"' -f4)
        export AWS_SESSION_TOKEN=$(echo "$ASSUME_ROLE_OUTPUT" | grep -o '"SessionToken": "[^"]*' | cut -d'"' -f4)
    fi
    
    if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] || [ -z "$AWS_SESSION_TOKEN" ]; then
        echo -e "${RED}Error: Failed to extract credentials from assume-role response${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Successfully assumed role${NC}"
    echo ""
fi

# Display required permissions information
echo -e "NOTE: Running this script requires that you be authenticated as an AWS user/role with the following permissions on the bucket:"
echo "  • s3:ListBucket         (to list objects in the bucket)"
echo "  • s3:GetObject          (to get object metadata including creation time)"
echo "  • s3:GetObjectTagging   (required for copy operation)"
echo "  • s3:PutObject          (to copy objects to themselves and trigger write events)"
echo "  • s3:PutObjectTagging   (required for copy operation)"
echo ""
echo -e "${BLUE}A custom policy or appropriate role${NC} should provide these permissions."
echo ""
echo -e "NOTE: This script adds/updates a 'psoxy-last-replay' metadata field on each object."
echo "      Existing metadata, tags, and content are preserved."
echo ""

# Handle single object mode
if [[ "$SINGLE_OBJECT_MODE" == true ]]; then
    echo -e "Single object mode: Replaying write on ${BLUE}s3://$BUCKET_NAME/$OBJECT_KEY${NC}"
    echo ""
    
    # Perform the write replay operation on the single object by copying to itself
    # AWS requires changing something when copying to self, so we add a metadata field
    TIMESTAMP_RFC3339=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    ERROR_OUTPUT=$(mktemp)
    trap 'rm -f "$ERROR_OUTPUT"' EXIT
    
    # Get existing metadata
    EXISTING_METADATA=$(aws s3api head-object --bucket "$BUCKET_NAME" --key "$OBJECT_KEY" --query 'Metadata' --output json 2>"$ERROR_OUTPUT")
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ Failed to get object metadata${NC}"
        cat "$ERROR_OUTPUT"
        exit 1
    fi
    
    # Merge existing metadata with new field
    if command -v jq &> /dev/null; then
        MERGED_METADATA=$(echo "$EXISTING_METADATA" | jq --arg ts "$TIMESTAMP_RFC3339" '. + {"psoxy-last-replay": $ts}')
    else
        # Fallback: if no existing metadata, just use the new field
        if [ "$EXISTING_METADATA" = "{}" ] || [ "$EXISTING_METADATA" = "null" ]; then
            MERGED_METADATA="{\"psoxy-last-replay\":\"$TIMESTAMP_RFC3339\"}"
        else
            # Simple merge without jq - append to existing
            MERGED_METADATA=$(echo "$EXISTING_METADATA" | sed 's/}$//' | sed 's/$/,"psoxy-last-replay":"'"$TIMESTAMP_RFC3339"'"}/')
        fi
    fi
    
    if aws s3api copy-object \
        --bucket "$BUCKET_NAME" \
        --copy-source "$BUCKET_NAME/$OBJECT_KEY" \
        --key "$OBJECT_KEY" \
        --metadata "$MERGED_METADATA" \
        --metadata-directive REPLACE \
        --tagging-directive COPY \
        > /dev/null 2>"$ERROR_OUTPUT"; then
        echo -e "${GREEN}✓ Successfully replayed write on s3://$BUCKET_NAME/$OBJECT_KEY${NC}"
        exit 0
    else
        echo -e "${RED}✗ Failed to replay write on s3://$BUCKET_NAME/$OBJECT_KEY${NC}"
        ERROR_MSG=$(cat "$ERROR_OUTPUT")
        if echo "$ERROR_MSG" | grep -q -i "AccessDenied\|Forbidden\|403"; then
            echo -e "${RED}Permission Error: Access denied. Please verify you have the required permissions.${NC}"
        fi
        echo "$ERROR_MSG"
        exit 1
    fi
fi

# Create temporary file to store object list
TEMP_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE"' EXIT

# List objects created after the timestamp and store in temp file
echo -e "Enumerating objects created after ${BLUE}$TIMESTAMP${NC}..."

# Convert our timestamp to epoch for comparison
TIMESTAMP_EPOCH=""
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    TIMESTAMP_EPOCH=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$TIMESTAMP" +%s)
else
    # Linux
    TIMESTAMP_EPOCH=$(date -d "$TIMESTAMP" +%s)
fi

# Filter objects by creation time
echo "Getting list of objects in bucket and filtering by creation time..."
FILTERED_FILE=$(mktemp)
ERROR_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE" "$FILTERED_FILE" "$ERROR_FILE"' EXIT

# Use AWS CLI to list objects with their LastModified timestamp
# Note: S3 doesn't expose creation time, so we use LastModified as a proxy
LIST_OUTPUT=$(aws s3api list-objects-v2 --bucket "$BUCKET_NAME" --query 'Contents[].[Key,LastModified]' --output text 2>"$ERROR_FILE")
LIST_EXIT_CODE=$?

if [ $LIST_EXIT_CODE -ne 0 ]; then
    echo -e "${RED}Error: Failed to list objects in bucket${NC}"
    ERROR_MSG=$(cat "$ERROR_FILE")
    if echo "$ERROR_MSG" | grep -q -i "AccessDenied\|Forbidden\|403"; then
        echo -e "${RED}Permission Error: Access denied. Please verify you have s3:ListBucket permission on the bucket.${NC}"
    elif echo "$ERROR_MSG" | grep -q -i "NoSuchBucket"; then
        echo -e "${RED}Error: Bucket does not exist or you don't have permission to access it.${NC}"
    fi
    echo "$ERROR_MSG"
    exit 1
fi

echo "$LIST_OUTPUT" | while read -r key last_modified; do
    # Skip if empty
    if [ -z "$key" ]; then
        continue
    fi
    
    # Convert last_modified to epoch
    # AWS returns timestamps in format: 2024-01-01T12:34:56.000Z or 2024-01-01T12:34:56+00:00
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS - handle both formats
        modified_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%S" "${last_modified:0:19}" +%s 2>/dev/null || \
                        date -j -f "%Y-%m-%dT%H:%M:%SZ" "$last_modified" +%s 2>/dev/null)
    else
        # Linux
        modified_epoch=$(date -d "$last_modified" +%s 2>/dev/null)
    fi
    
    # If modified time is after our timestamp, include this object
    if [ -n "$modified_epoch" ] && [ "$modified_epoch" -gt "$TIMESTAMP_EPOCH" ]; then
        echo "$key" >> "$FILTERED_FILE"
    fi
done

# Move filtered results to temp file
mv "$FILTERED_FILE" "$TEMP_FILE"

# Count total objects to process
TOTAL_OBJECTS=$(wc -l < "$TEMP_FILE" | tr -d ' ')

if [ "$TOTAL_OBJECTS" -eq 0 ]; then
    echo -e "${YELLOW}No objects found modified after $TIMESTAMP${NC}"
    exit 0
fi

echo -e "Found ${BLUE}$TOTAL_OBJECTS${NC} objects to replay writes on"
echo ""

# Process each object
CURRENT=0
SUCCESS_COUNT=0
FAILED_COUNT=0

while IFS= read -r object_key; do
    CURRENT=$((CURRENT + 1))
    echo -e "[$CURRENT/$TOTAL_OBJECTS] Replaying write of object: ${BLUE}s3://$BUCKET_NAME/$object_key${NC}"
    
    # Perform the write replay operation by copying object to itself
    # AWS requires changing something when copying to self, so we add a metadata field
    TIMESTAMP_RFC3339=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    ERROR_OUTPUT=$(mktemp)
    
    # Get existing metadata
    EXISTING_METADATA=$(aws s3api head-object --bucket "$BUCKET_NAME" --key "$object_key" --query 'Metadata' --output json 2>"$ERROR_OUTPUT")
    if [ $? -ne 0 ]; then
        FAILED_COUNT=$((FAILED_COUNT + 1))
        echo -e "  ${RED}✗ Failed to get object metadata${NC}"
        if [ "$FAILED_COUNT" -le 3 ]; then
            cat "$ERROR_OUTPUT"
        fi
        rm -f "$ERROR_OUTPUT"
        continue
    fi
    
    # Merge existing metadata with new field
    if command -v jq &> /dev/null; then
        MERGED_METADATA=$(echo "$EXISTING_METADATA" | jq --arg ts "$TIMESTAMP_RFC3339" '. + {"psoxy-last-replay": $ts}')
    else
        # Fallback: if no existing metadata, just use the new field
        if [ "$EXISTING_METADATA" = "{}" ] || [ "$EXISTING_METADATA" = "null" ]; then
            MERGED_METADATA="{\"psoxy-last-replay\":\"$TIMESTAMP_RFC3339\"}"
        else
            # Simple merge without jq - append to existing
            MERGED_METADATA=$(echo "$EXISTING_METADATA" | sed 's/}$//' | sed 's/$/,"psoxy-last-replay":"'"$TIMESTAMP_RFC3339"'"}/')
        fi
    fi
    
    if aws s3api copy-object \
        --bucket "$BUCKET_NAME" \
        --copy-source "$BUCKET_NAME/$object_key" \
        --key "$object_key" \
        --metadata "$MERGED_METADATA" \
        --metadata-directive REPLACE \
        --tagging-directive COPY \
        > /dev/null 2>"$ERROR_OUTPUT"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "  ${GREEN}✓ Success${NC}"
    else
        FAILED_COUNT=$((FAILED_COUNT + 1))
        ERROR_MSG=$(cat "$ERROR_OUTPUT")
        if echo "$ERROR_MSG" | grep -q -i "AccessDenied\|Forbidden\|403"; then
            echo -e "  ${RED}✗ Failed - Permission Error${NC}"
        else
            echo -e "  ${RED}✗ Failed${NC}"
        fi
        # Only show detailed error for first few failures to avoid spam
        if [ "$FAILED_COUNT" -le 3 ]; then
            echo "    Error: $ERROR_MSG"
        fi
    fi
    rm -f "$ERROR_OUTPUT"
    
    # Add small delay to avoid overwhelming the API
    sleep 0.1
    
done < "$TEMP_FILE"

echo ""
echo -e "Write replay process completed!"
echo -e "Total objects processed: ${BLUE}$TOTAL_OBJECTS${NC}"
echo -e "Successful write replays: ${GREEN}$SUCCESS_COUNT${NC}"
echo -e "Failed write replays: ${RED}$FAILED_COUNT${NC}"

if [ "$FAILED_COUNT" -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}Note: If you encountered permission errors, consider:${NC}"
    echo "  • Using --role option to assume a role with appropriate permissions"
    echo "  • Verifying your IAM user/role has the required S3 permissions"
    exit 1
fi

