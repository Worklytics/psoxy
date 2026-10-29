#!/bin/bash

# Script to replay writes on GCS objects created since a specific timestamp
# (useful to re-trigger bulk processing of those objects via proxy)
# Usage: ./replay-gcs-writes.sh [--prefix PREFIX] <bucket_name> [timestamp]
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

# shellcheck source=gcs-bulk-tool-common.sh
source "$(dirname "$0")/gcs-bulk-tool-common.sh"

# Function to display usage information
usage() {
    echo "Usage: $0 [--prefix PREFIX] <bucket_name> [timestamp]"
    echo "   OR: $0 <object_path>"
    echo ""
    echo "Options:"
    echo "  --prefix PREFIX    : GCS object prefix to limit listing (bucket mode only)"
    echo ""
    echo "Arguments:"
    echo "  bucket_name  : GCS bucket name (with or without gs:// prefix)"
    echo "  timestamp    : ISO 8601 timestamp (YYYY-MM-DDTHH:MM:SSZ) - optional"
    echo "                If not provided, defaults to one week ago"
    echo "  object_path  : GCS object path (with or without gs:// prefix) to replay a single object"
    echo ""
    echo "Examples:"
    echo "  $0 my-bucket                                    # Replay writes on objects from last week"
    echo "  $0 gs://my-bucket                               # Replay writes on objects from last week (gs:// prefix accepted)"
    echo "  $0 my-bucket 2024-01-01T00:00:00Z              # Replay writes on objects since specific date"
    echo "  $0 --prefix exports/ my-bucket                 # Replay writes under a prefix"
    echo "  $0 gs://my-bucket/path/to/object.json          # Replay write on a single object"
    echo "  $0 my-bucket/path/to/object.json               # Replay write on a single object (gs:// added automatically)"
    echo ""
    echo "This script will:"
    echo "  • If bucket_name provided: List all objects in the bucket created after the specified timestamp and replay writes"
    echo "  • If object_path provided: Replay write on just that single object using 'gsutil rewrite -kO'"
    exit 1
}

PREFIX=""
while [ $# -gt 0 ]; do
    case "$1" in
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

PREFIX=$(gcs_bulk_tool_strip_gs_uri "$PREFIX")
if [[ "$PREFIX" == gs://* ]]; then
    PREFIX="${PREFIX#gs://}"
    if [[ "$PREFIX" == */* ]]; then
        PREFIX="${PREFIX#*/}"
    fi
fi
if [ -n "$PREFIX" ] && [[ "$PREFIX" != */ ]]; then
    PREFIX="${PREFIX}/"
fi

# Check if correct number of arguments provided
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Error: Invalid number of arguments"
    usage
fi

FIRST_ARG="$1"

# Check if first argument is an object path (contains a slash after the bucket name)
# For gs:// URLs, check if there's a slash after the bucket name
# For non-gs:// URLs, check if there's a slash (indicating bucket/path structure)
if [[ "$FIRST_ARG" == gs://*/* ]] || ([[ "$FIRST_ARG" != gs://* ]] && [[ "$FIRST_ARG" == */* ]]); then
    # Single object mode
    if [ $# -ne 1 ]; then
        echo "Error: When providing an object path, no additional arguments are allowed"
        usage
    fi
    if [ -n "$PREFIX" ]; then
        echo "Error: --prefix is only supported in bucket mode, not when replaying a single object path"
        usage
    fi
    # Add gs:// prefix if not present
    if [[ "$FIRST_ARG" == gs://* ]]; then
        OBJECT_PATH="$FIRST_ARG"
    else
        OBJECT_PATH="gs://$FIRST_ARG"
    fi
    SINGLE_OBJECT_MODE=true
else
    # Bucket mode
    # Remove gs:// prefix if present for bucket name
    if [[ "$FIRST_ARG" == gs://* ]]; then
        BUCKET_NAME="${FIRST_ARG#gs://}"
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

# Check if gsutil is available
if ! command -v gsutil &> /dev/null; then
    echo "Error: gsutil command not found. Please install Google Cloud SDK."
    exit 1
fi

# Check if user is authenticated
if ! gsutil ls &> /dev/null; then
    echo "Error: Not authenticated with Google Cloud. Please run 'gcloud auth login'"
    exit 1
fi

# Display required permissions information
echo -e "NOTE: Running this script requires that you be authenticated as a GCP user with the following permissions on the bucket:"
echo "  • storage.objects.list    (to list objects in the bucket)"
echo "  • storage.objects.get     (to get object metadata including creation time)"
echo "  • storage.objects.update  (to rewrite objects and trigger write events)"
echo ""
echo -e "${INFO}roles/storage.objectAdmin${NC} is the least-privileged predefined role that provides these permissions."
echo ""

# Handle single object mode
if [[ "$SINGLE_OBJECT_MODE" == true ]]; then
    echo -e "Single object mode: Replaying write on ${INFO}$OBJECT_PATH${NC}"
    echo ""
    
    # Perform the write replay operation on the single object
    if gsutil rewrite -kO "$OBJECT_PATH" > /dev/null 2>&1; then
        echo -e "${SUCCESS}✓ Successfully replayed write on $OBJECT_PATH${NC}"
        exit 0
    else
        echo -e "${ERR}✗ Failed to replay write on $OBJECT_PATH${NC}"
        exit 1
    fi
fi

# Create temporary file to store object list
TEMP_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE"' EXIT

# List objects created after the timestamp and store in temp file
echo -e "Enumerating objects updated after ${INFO}$TIMESTAMP${NC}..."
if [ -n "$PREFIX" ]; then
    echo -e "Filtering to prefix: ${INFO}$PREFIX${NC}"
fi

TIMESTAMP_EPOCH=$(gcs_bulk_tool_timestamp_to_epoch "$TIMESTAMP")

FILTERED_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE" "$FILTERED_FILE"' EXIT

echo "Getting list of objects in bucket and filtering by update time..."
gcs_bulk_tool_list_input_objects_modified_after "$BUCKET_NAME" "$PREFIX" "$TIMESTAMP_EPOCH" "$FILTERED_FILE"

while IFS=$'\t' read -r _updated object_path_key; do
    echo "gs://$BUCKET_NAME/$object_path_key" >> "$TEMP_FILE"
done < "$FILTERED_FILE"

# Count total objects to process
TOTAL_OBJECTS=$(wc -l < "$TEMP_FILE" | tr -d ' ')

if [ "$TOTAL_OBJECTS" -eq 0 ]; then
    echo -e "${WARN}No objects found created after $TIMESTAMP${NC}"
    exit 0
fi

echo -e "Found ${INFO}$TOTAL_OBJECTS${NC} objects to replay writes on"
echo ""

# Process each object
CURRENT=0
SUCCESS_COUNT=0
FAILED_COUNT=0

while IFS= read -r object_path; do
    CURRENT=$((CURRENT + 1))
    echo -e "[$CURRENT/$TOTAL_OBJECTS] Replaying write of object: ${INFO}$object_path${NC}"
    
    # Perform the write replay operation
    if gsutil rewrite -kO "$object_path" > /dev/null 2>&1; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "  ${SUCCESS}✓ Success${NC}"
    else
        FAILED_COUNT=$((FAILED_COUNT + 1))
        echo -e "  ${ERR}✗ Failed${NC}"
    fi
    
    # Add small delay to avoid overwhelming the API
    sleep 0.1
    
done < "$TEMP_FILE"

echo ""
echo -e "Write replay process completed!"
echo -e "Total objects processed: ${INFO}$TOTAL_OBJECTS${NC}"
echo -e "Successful write replays: ${SUCCESS}$SUCCESS_COUNT${NC}"
echo -e "Failed write replays: ${ERR}$FAILED_COUNT${NC}"

if [ "$FAILED_COUNT" -gt 0 ]; then
    exit 1
fi
