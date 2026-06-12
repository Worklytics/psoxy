#!/bin/bash

# Script to replay writes on S3 objects created since a specific timestamp
# (useful to re-trigger bulk processing of those objects via proxy)
# Usage: ./replay-s3-writes.sh [--role ROLE_ARN] [--prefix PREFIX] <bucket_name> [timestamp]
# Timestamp format: YYYY-MM-DDTHH:MM:SSZ (ISO 8601)
# If timestamp is not provided, defaults to one week ago

set -euo pipefail

# Color definitions
COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# Resolve a Python 3 interpreter (python3 preferred, then python)
resolve_python() {
    if command -v python3 &> /dev/null; then
        echo python3
        return 0
    fi
    if command -v python &> /dev/null && python -c 'import sys; sys.exit(0 if sys.version_info >= (3, 0) else 1)' 2>/dev/null; then
        echo python
        return 0
    fi
    return 1
}

# Normalize metadata from head-object (empty/null query results become "{}")
normalize_existing_metadata() {
    local metadata="$1"
    if [ -z "$metadata" ] || [ "$metadata" = "null" ]; then
        echo "{}"
    else
        echo "$metadata"
    fi
}

# Merge existing object metadata with psoxy-last-replay; output compact JSON for --metadata
merge_replay_metadata() {
    local existing="$1"
    local ts="$2"

    existing=$(normalize_existing_metadata "$existing")

    if command -v jq &> /dev/null; then
        local merged
        merged=$(echo "$existing" | jq -c --arg ts "$ts" '
            (if type == "object" then . else {} end) + {"psoxy-last-replay": $ts}
        ')
        if [ -z "$merged" ]; then
            echo "Error: failed to build metadata JSON (jq returned empty output)" >&2
            return 1
        fi
        echo "$merged"
        return 0
    fi

    local python_cmd
    if python_cmd=$(resolve_python); then
        "$python_cmd" -c '
import json, sys
raw = sys.argv[1]
ts = sys.argv[2]
try:
    metadata = json.loads(raw) if raw else {}
except json.JSONDecodeError:
    metadata = {}
if not isinstance(metadata, dict):
    metadata = {}
metadata["psoxy-last-replay"] = ts
print(json.dumps(metadata, separators=(",", ":")))
' "$existing" "$ts"
        return 0
    fi

    if [ "$existing" = "{}" ]; then
        echo "{\"psoxy-last-replay\":\"$ts\"}"
        return 0
    fi

    echo "Error: jq or python is required" >&2
    return 1
}

# Function to display usage information
usage() {
    echo "Usage: $0 [--role ROLE_ARN] [--prefix PREFIX] <bucket_name> [timestamp]"
    echo "   OR: $0 [--role ROLE_ARN] <object_path>"
    echo ""
    echo "Options:"
    echo "  --role ROLE_ARN    : IAM role ARN to assume before accessing S3"
    echo "  --prefix PREFIX    : S3 key prefix to limit listing (bucket mode only)"
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
    echo "  $0 --prefix raw/source_bucket=my-bucket my-bucket  # Replay writes under a prefix"
    echo "  $0 s3://my-bucket/path/to/object.json          # Replay write on a single object"
    echo "  $0 my-bucket/path/to/object.json               # Replay write on a single object (s3:// added automatically)"
    echo "  $0 --role arn:aws:iam::123456789012:role/MyRole my-bucket  # Assume role before accessing bucket"
    echo ""
    echo "This script will:"
    echo "  • If bucket_name provided: List objects in the bucket (optionally under --prefix) modified after the timestamp and replay writes"
    echo "  • If object_path provided: Replay write on just that single object using 'aws s3api copy-object'"
    echo ""
    echo "Note: This script adds/updates 'psoxy-last-replay' metadata field to trigger S3 events."
    echo "      Existing metadata, tags, and content are preserved."
    exit 1
}

# Parse options
ROLE_ARN=""
PREFIX=""
while [ $# -gt 0 ]; do
    case "$1" in
        --role)
            if [ $# -lt 2 ]; then
                echo "Error: --role option requires a role ARN"
                usage
            fi
            ROLE_ARN="$2"
            shift 2
            ;;
        --prefix)
            if [ $# -lt 2 ]; then
                echo "Error: --prefix option requires a prefix value"
                usage
            fi
            PREFIX="$2"
            shift 2
            ;;
        --)
            shift
            break
            ;;
        -*)
            echo "Error: Unknown option: $1"
            usage
            ;;
        *)
            break
            ;;
    esac
done

# Strip s3:// from prefix if provided
if [[ "$PREFIX" == s3://* ]]; then
    PREFIX="${PREFIX#s3://}"
    # If prefix included bucket name, strip it when bucket mode supplies bucket separately
    if [[ "$PREFIX" == */* ]]; then
        PREFIX="${PREFIX#*/}"
    fi
fi

# Check if correct number of arguments provided (after shifting options)
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
    if [ -n "$PREFIX" ]; then
        echo "Error: --prefix is only supported in bucket mode, not when replaying a single object path"
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
        echo -e "No timestamp provided, defaulting to one week ago: ${INFO}$TIMESTAMP${NC}"
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
    echo -e "${ERR}Error: Not authenticated with AWS. Please configure your AWS credentials.${NC}"
    exit 1
fi

# Assume role if provided
if [ -n "$ROLE_ARN" ]; then
    echo -e "Assuming role: ${INFO}$ROLE_ARN${NC}"

    # Generate a session name
    SESSION_NAME="replay-s3-writes-$(date +%s)"

    # Assume the role and capture credentials
    ASSUME_ROLE_OUTPUT=$(aws sts assume-role \
        --role-arn "$ROLE_ARN" \
        --role-session-name "$SESSION_NAME" \
        --output json 2>&1)

    if [ $? -ne 0 ]; then
        echo -e "${ERR}Error: Failed to assume role${NC}"
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
        echo -e "${ERR}Error: Failed to extract credentials from assume-role response${NC}"
        exit 1
    fi

    echo -e "${SUCCESS}✓ Successfully assumed role${NC}"
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
echo -e "${INFO}A custom policy or appropriate role${NC} should provide these permissions."
echo ""
echo -e "NOTE: This script adds/updates a 'psoxy-last-replay' metadata field on each object."
echo "      Existing metadata, tags, and content are preserved."
echo ""

replay_object_write() {
    local bucket="$1"
    local object_key="$2"
    local error_output="$3"

    local timestamp_rfc3339
    timestamp_rfc3339=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    local existing_metadata
    existing_metadata=$(aws s3api head-object --bucket "$bucket" --key "$object_key" --query 'Metadata' --output json 2>"$error_output")
    if [ $? -ne 0 ]; then
        return 2
    fi

    local merged_metadata merge_error
    merge_error=$(mktemp)
    if ! merged_metadata=$(merge_replay_metadata "$existing_metadata" "$timestamp_rfc3339" 2>"$merge_error"); then
        echo "Error: failed to merge metadata for s3://$bucket/$object_key" >&2
        cat "$merge_error" >&2
        rm -f "$merge_error"
        return 3
    fi
    rm -f "$merge_error"

    if [ -z "$merged_metadata" ]; then
        echo "Error: merged metadata is empty for s3://$bucket/$object_key" >&2
        return 3
    fi

    aws s3api copy-object \
        --bucket "$bucket" \
        --copy-source "$bucket/$object_key" \
        --key "$object_key" \
        --metadata "$merged_metadata" \
        --metadata-directive REPLACE \
        --tagging-directive COPY \
        > /dev/null 2>"$error_output"
}

# Handle single object mode
if [[ "$SINGLE_OBJECT_MODE" == true ]]; then
    echo -e "Single object mode: Replaying write on ${INFO}s3://$BUCKET_NAME/$OBJECT_KEY${NC}"
    echo ""

    ERROR_OUTPUT=$(mktemp)
    trap 'rm -f "$ERROR_OUTPUT"' EXIT

    if replay_object_write "$BUCKET_NAME" "$OBJECT_KEY" "$ERROR_OUTPUT"; then
        echo -e "${SUCCESS}✓ Successfully replayed write on s3://$BUCKET_NAME/$OBJECT_KEY${NC}"
        exit 0
    fi

    echo -e "${ERR}✗ Failed to replay write on s3://$BUCKET_NAME/$OBJECT_KEY${NC}"
    ERROR_MSG=$(cat "$ERROR_OUTPUT")
    if echo "$ERROR_MSG" | grep -q -i "AccessDenied\|Forbidden\|403"; then
        echo -e "${ERR}Permission Error: Access denied. Please verify you have the required permissions.${NC}"
    fi
    echo "$ERROR_MSG"
    exit 1
fi

# Create temporary file to store object list
TEMP_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE"' EXIT

# List objects created after the timestamp and store in temp file
echo -e "Enumerating objects modified after ${INFO}$TIMESTAMP${NC}..."
if [ -n "$PREFIX" ]; then
    echo -e "Filtering to prefix: ${INFO}$PREFIX${NC}"
fi

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
echo "Getting list of objects in bucket and filtering by modification time..."
FILTERED_FILE=$(mktemp)
ERROR_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE" "$FILTERED_FILE" "$ERROR_FILE"' EXIT

append_objects_modified_after() {
    local key="$1"
    local last_modified="$2"

    if [ -z "$key" ]; then
        return 0
    fi

    local modified_epoch=""
    # AWS returns timestamps in format: 2024-01-01T12:34:56.000Z or 2024-01-01T12:34:56+00:00
    if [[ "$OSTYPE" == "darwin"* ]]; then
        modified_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%S" "${last_modified:0:19}" +%s 2>/dev/null || \
                        date -j -f "%Y-%m-%dT%H:%M:%SZ" "$last_modified" +%s 2>/dev/null)
    else
        modified_epoch=$(date -d "$last_modified" +%s 2>/dev/null)
    fi

    if [ -n "$modified_epoch" ] && [ "$modified_epoch" -gt "$TIMESTAMP_EPOCH" ]; then
        echo "$key" >> "$FILTERED_FILE"
    fi
}

CONTINUATION_TOKEN=""
while true; do
    LIST_ARGS=(s3api list-objects-v2 --bucket "$BUCKET_NAME" --output json)
    if [ -n "$PREFIX" ]; then
        LIST_ARGS+=(--prefix "$PREFIX")
    fi
    if [ -n "$CONTINUATION_TOKEN" ]; then
        LIST_ARGS+=(--starting-token "$CONTINUATION_TOKEN")
    fi

    PAGE_JSON=$(aws "${LIST_ARGS[@]}" 2>"$ERROR_FILE")
    LIST_EXIT_CODE=$?

    if [ $LIST_EXIT_CODE -ne 0 ]; then
        echo -e "${ERR}Error: Failed to list objects in bucket${NC}"
        ERROR_MSG=$(cat "$ERROR_FILE")
        if echo "$ERROR_MSG" | grep -q -i "AccessDenied\|Forbidden\|403"; then
            echo -e "${ERR}Permission Error: Access denied. Please verify you have s3:ListBucket permission on the bucket.${NC}"
        elif echo "$ERROR_MSG" | grep -q -i "NoSuchBucket"; then
            echo -e "${ERR}Error: Bucket does not exist or you don't have permission to access it.${NC}"
        fi
        echo "$ERROR_MSG"
        exit 1
    fi

    if command -v jq &> /dev/null; then
        while IFS=$'\t' read -r key last_modified; do
            append_objects_modified_after "$key" "$last_modified"
        done < <(echo "$PAGE_JSON" | jq -r '.Contents[]? | [.Key, .LastModified] | @tsv')
        CONTINUATION_TOKEN=$(echo "$PAGE_JSON" | jq -r '.NextContinuationToken // empty')
    elif python_cmd=$(resolve_python); then
        while IFS=$'\t' read -r key last_modified; do
            append_objects_modified_after "$key" "$last_modified"
        done < <("$python_cmd" -c 'import json,sys
page=json.load(sys.stdin)
for item in page.get("Contents") or []:
    print("{}\t{}".format(item["Key"], item["LastModified"]))' <<< "$PAGE_JSON")
        CONTINUATION_TOKEN=$("$python_cmd" -c 'import json,sys; print(json.load(sys.stdin).get("NextContinuationToken") or "")' <<< "$PAGE_JSON")
    else
        TEXT_ARGS=(s3api list-objects-v2 --bucket "$BUCKET_NAME" --query 'Contents[].[Key,LastModified]' --output text)
        TOKEN_ARGS=(s3api list-objects-v2 --bucket "$BUCKET_NAME" --query 'NextContinuationToken' --output text)
        if [ -n "$PREFIX" ]; then
            TEXT_ARGS+=(--prefix "$PREFIX")
            TOKEN_ARGS+=(--prefix "$PREFIX")
        fi
        if [ -n "$CONTINUATION_TOKEN" ]; then
            TEXT_ARGS+=(--starting-token "$CONTINUATION_TOKEN")
            TOKEN_ARGS+=(--starting-token "$CONTINUATION_TOKEN")
        fi

        LIST_OUTPUT=$(aws "${TEXT_ARGS[@]}" 2>"$ERROR_FILE")
        while read -r key last_modified; do
            append_objects_modified_after "$key" "$last_modified"
        done <<< "$LIST_OUTPUT"
        CONTINUATION_TOKEN=$(aws "${TOKEN_ARGS[@]}" 2>"$ERROR_FILE")
    fi

    if [ -z "$CONTINUATION_TOKEN" ] || [ "$CONTINUATION_TOKEN" = "None" ]; then
        break
    fi
done

# Move filtered results to temp file
mv "$FILTERED_FILE" "$TEMP_FILE"

# Count total objects to process
TOTAL_OBJECTS=$(wc -l < "$TEMP_FILE" | tr -d ' ')

if [ "$TOTAL_OBJECTS" -eq 0 ]; then
    echo -e "${WARN}No objects found modified after $TIMESTAMP${NC}"
    exit 0
fi

echo -e "Found ${INFO}$TOTAL_OBJECTS${NC} objects to replay writes on"
echo ""

# Process each object
CURRENT=0
SUCCESS_COUNT=0
FAILED_COUNT=0

while IFS= read -r object_key; do
    CURRENT=$((CURRENT + 1))
    echo -e "[$CURRENT/$TOTAL_OBJECTS] Replaying write of object: ${INFO}s3://$BUCKET_NAME/$object_key${NC}"

    ERROR_OUTPUT=$(mktemp)
    REPLAY_EXIT=0
    replay_object_write "$BUCKET_NAME" "$object_key" "$ERROR_OUTPUT" || REPLAY_EXIT=$?

    if [ "$REPLAY_EXIT" -eq 0 ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "  ${SUCCESS}✓ Success${NC}"
    else
        FAILED_COUNT=$((FAILED_COUNT + 1))
        ERROR_MSG=$(cat "$ERROR_OUTPUT")
        if [ "$REPLAY_EXIT" -eq 2 ] && echo "$ERROR_MSG" | grep -q -i "AccessDenied\|Forbidden\|403"; then
            echo -e "  ${ERR}✗ Failed - Permission Error${NC}"
        elif [ "$REPLAY_EXIT" -eq 3 ]; then
            echo -e "  ${ERR}✗ Failed - Metadata merge error${NC}"
        else
            echo -e "  ${ERR}✗ Failed${NC}"
        fi
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
echo -e "Total objects processed: ${INFO}$TOTAL_OBJECTS${NC}"
echo -e "Successful write replays: ${SUCCESS}$SUCCESS_COUNT${NC}"
echo -e "Failed write replays: ${ERR}$FAILED_COUNT${NC}"

if [ "$FAILED_COUNT" -gt 0 ]; then
    echo ""
    echo -e "${WARN}Note: If you encountered permission errors, consider:${NC}"
    echo "  • Using --role option to assume a role with appropriate permissions"
    echo "  • Verifying your IAM user/role has the required S3 permissions"
    exit 1
fi
