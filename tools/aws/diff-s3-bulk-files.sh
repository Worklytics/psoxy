#!/bin/bash

# List objects present in a bulk connector -input bucket but missing from the matching -sanitized
# bucket (within a time window). Useful when debugging bulk processing failures.
#
# Usage: ./diff-s3-bulk-files.sh [options] <input_bucket> [<sanitized_bucket>] [timestamp]
# Timestamp format: YYYY-MM-DDTHH:MM:SSZ (ISO 8601). Defaults to one week ago.

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
# shellcheck source=../set-term-colorscheme.sh
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

s3_bulk_tool_default_timestamp_one_week_ago() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        date -u -v-7d '+%Y-%m-%dT%H:%M:%SZ'
    else
        date -u -d '1 week ago' '+%Y-%m-%dT%H:%M:%SZ'
    fi
}

s3_bulk_tool_timestamp_to_epoch() {
    local timestamp="$1"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        date -j -f "%Y-%m-%dT%H:%M:%SZ" "$timestamp" +%s
    else
        date -d "$timestamp" +%s
    fi
}

s3_bulk_tool_last_modified_to_epoch() {
    local last_modified="$1"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        date -j -f "%Y-%m-%dT%H:%M:%S" "${last_modified:0:19}" +%s 2>/dev/null || \
            date -j -f "%Y-%m-%dT%H:%M:%SZ" "$last_modified" +%s 2>/dev/null
    else
        date -d "$last_modified" +%s 2>/dev/null
    fi
}

s3_bulk_tool_validate_timestamp() {
    local timestamp="$1"
    [[ "$timestamp" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]
}

s3_bulk_tool_strip_s3_uri() {
    local value="$1"
    if [[ "$value" == s3://* ]]; then
        value="${value#s3://}"
    fi
    echo "$value"
}

s3_bulk_tool_derive_sanitized_bucket() {
    local input_bucket="$1"
    if [[ "$input_bucket" == *-input ]]; then
        echo "${input_bucket%-input}-sanitized"
    else
        echo ""
    fi
}

s3_bulk_tool_expected_sanitized_key() {
    local input_key="$1"
    local input_base_path="$2"
    local output_base_path="$3"
    local relative="$input_key"
    if [ -n "$input_base_path" ]; then
        if [[ "$relative" == "$input_base_path"* ]]; then
            relative="${relative#$input_base_path}"
        fi
    fi
    echo "${output_base_path}${relative}"
}

s3_bulk_tool_assume_role_if_needed() {
    local role_arn="$1"
    local session_prefix="$2"

    if [ -z "$role_arn" ]; then
        return 0
    fi

    echo -e "Assuming role: ${INFO}$role_arn${NC}"

    local session_name="${session_prefix}-$(date +%s)"
    local assume_role_output
    assume_role_output=$(aws sts assume-role \
        --role-arn "$role_arn" \
        --role-session-name "$session_name" \
        --output json 2>&1) || {
        echo -e "${ERR}Error: Failed to assume role${NC}"
        echo "$assume_role_output"
        return 1
    }

    if command -v jq &> /dev/null; then
        export AWS_ACCESS_KEY_ID
        export AWS_SECRET_ACCESS_KEY
        export AWS_SESSION_TOKEN
        AWS_ACCESS_KEY_ID=$(echo "$assume_role_output" | jq -r '.Credentials.AccessKeyId')
        AWS_SECRET_ACCESS_KEY=$(echo "$assume_role_output" | jq -r '.Credentials.SecretAccessKey')
        AWS_SESSION_TOKEN=$(echo "$assume_role_output" | jq -r '.Credentials.SessionToken')
    else
        export AWS_ACCESS_KEY_ID
        export AWS_SECRET_ACCESS_KEY
        export AWS_SESSION_TOKEN
        AWS_ACCESS_KEY_ID=$(echo "$assume_role_output" | grep -o '"AccessKeyId": "[^"]*' | cut -d'"' -f4)
        AWS_SECRET_ACCESS_KEY=$(echo "$assume_role_output" | grep -o '"SecretAccessKey": "[^"]*' | cut -d'"' -f4)
        AWS_SESSION_TOKEN=$(echo "$assume_role_output" | grep -o '"SessionToken": "[^"]*' | cut -d'"' -f4)
    fi

    if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] || [ -z "$AWS_SESSION_TOKEN" ]; then
        echo -e "${ERR}Error: Failed to extract credentials from assume-role response${NC}"
        return 1
    fi

    echo -e "${SUCCESS}✓ Successfully assumed role${NC}"
    echo ""
    return 0
}

s3_bulk_tool_list_input_objects_modified_after() {
    local bucket="$1"
    local prefix="$2"
    local timestamp_epoch="$3"
    local outfile="$4"
    local error_file="$5"

    local continuation_token=""
    while true; do
        if command -v jq &> /dev/null; then
            local list_args=(s3api list-objects-v2 --bucket "$bucket" --output json)
            if [ -n "$prefix" ]; then
                list_args+=(--prefix "$prefix")
            fi
            if [ -n "$continuation_token" ]; then
                list_args+=(--starting-token "$continuation_token")
            fi

            local page_json
            page_json=$(aws "${list_args[@]}" 2>"$error_file") || return 1

            while IFS=$'\t' read -r last_modified key; do
                [ -z "$key" ] && continue
                local modified_epoch
                modified_epoch=$(s3_bulk_tool_last_modified_to_epoch "$last_modified")
                if [ -n "$modified_epoch" ] && [ "$modified_epoch" -gt "$timestamp_epoch" ]; then
                    printf '%s\t%s\n' "$last_modified" "$key" >> "$outfile"
                fi
            done < <(echo "$page_json" | jq -r '.Contents[]? | [.LastModified, .Key] | @tsv')
            continuation_token=$(echo "$page_json" | jq -r '.NextContinuationToken // empty')
        else
            local text_args=(s3api list-objects-v2 --bucket "$bucket" --query 'Contents[].[LastModified,Key]' --output text)
            local token_args=(s3api list-objects-v2 --bucket "$bucket" --query 'NextContinuationToken' --output text)
            if [ -n "$prefix" ]; then
                text_args+=(--prefix "$prefix")
                token_args+=(--prefix "$prefix")
            fi
            if [ -n "$continuation_token" ]; then
                text_args+=(--starting-token "$continuation_token")
                token_args+=(--starting-token "$continuation_token")
            fi

            local list_output
            list_output=$(aws "${text_args[@]}" 2>"$error_file") || return 1

            while read -r last_modified key; do
                [ -z "$key" ] && continue
                [ "$last_modified" = "None" ] && continue
                local modified_epoch
                modified_epoch=$(s3_bulk_tool_last_modified_to_epoch "$last_modified")
                if [ -n "$modified_epoch" ] && [ "$modified_epoch" -gt "$timestamp_epoch" ]; then
                    printf '%s\t%s\n' "$last_modified" "$key" >> "$outfile"
                fi
            done <<< "$list_output"

            continuation_token=$(aws "${token_args[@]}" 2>"$error_file")
        fi

        if [ -z "$continuation_token" ] || [ "$continuation_token" = "None" ]; then
            break
        fi
    done
    return 0
}

s3_bulk_tool_sanitized_object_exists() {
    local bucket="$1"
    local key="$2"
    aws s3api head-object --bucket "$bucket" --key "$key" > /dev/null 2>&1
}

usage() {
    echo "Usage: $0 [--role ROLE_ARN] [--prefix PREFIX] [--input-base-path PATH] [--output-base-path PATH] <input_bucket> [<sanitized_bucket>] [timestamp]"
    echo ""
    echo "Options:"
    echo "  --role ROLE_ARN         IAM role ARN to assume before accessing S3"
    echo "  --prefix PREFIX         List only input objects whose keys start with this prefix"
    echo "  --input-base-path PATH  Strip this prefix from input keys when computing expected sanitized keys"
    echo "                          (same as Psoxy INPUT_BASE_PATH)"
    echo "  --output-base-path PATH Prepend this prefix to sanitized keys"
    echo "                          (same as Psoxy OUTPUT_BASE_PATH)"
    echo ""
    echo "Arguments:"
    echo "  input_bucket     Bulk connector input bucket (with or without s3:// prefix)"
    echo "  sanitized_bucket Optional. Defaults to input name with trailing -input replaced by -sanitized"
    echo "  timestamp        Optional ISO 8601 time; only input objects modified after this time are checked"
    echo ""
    echo "Output:"
    echo "  Missing objects on stdout, newest first:  LAST_MODIFIED  s3://input-bucket/key"
    echo ""
    echo "Examples:"
    echo "  $0 myenv-zoom-input"
    echo "  $0 myenv-zoom-input myenv-zoom-sanitized 2024-06-01T00:00:00Z"
    echo "  $0 --prefix exports/ --role arn:aws:iam::123456789012:role/BulkDebugger myenv-zoom-input"
    exit 1
}

ROLE_ARN=""
PREFIX=""
INPUT_BASE_PATH=""
OUTPUT_BASE_PATH=""

while [ $# -gt 0 ]; do
    case "$1" in
        --role)
            [ $# -lt 2 ] && { echo "Error: --role requires a role ARN"; usage; }
            ROLE_ARN="$2"
            shift 2
            ;;
        --prefix)
            [ $# -lt 2 ] && { echo "Error: --prefix requires a value"; usage; }
            PREFIX="$2"
            shift 2
            ;;
        --input-base-path)
            [ $# -lt 2 ] && { echo "Error: --input-base-path requires a value"; usage; }
            INPUT_BASE_PATH="$2"
            shift 2
            ;;
        --output-base-path)
            [ $# -lt 2 ] && { echo "Error: --output-base-path requires a value"; usage; }
            OUTPUT_BASE_PATH="$2"
            shift 2
            ;;
        -h|--help)
            usage
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

if [ $# -lt 1 ] || [ $# -gt 3 ]; then
    echo "Error: Invalid number of arguments"
    usage
fi

INPUT_BUCKET=$(s3_bulk_tool_strip_s3_uri "$1")
shift

SANITIZED_BUCKET=""
TIMESTAMP=""

if [ $# -eq 0 ]; then
    SANITIZED_BUCKET=$(s3_bulk_tool_derive_sanitized_bucket "$INPUT_BUCKET")
    TIMESTAMP=$(s3_bulk_tool_default_timestamp_one_week_ago)
    echo -e "No timestamp provided, defaulting to one week ago: ${INFO}$TIMESTAMP${NC}"
elif [ $# -eq 1 ]; then
    if s3_bulk_tool_validate_timestamp "$1"; then
        SANITIZED_BUCKET=$(s3_bulk_tool_derive_sanitized_bucket "$INPUT_BUCKET")
        TIMESTAMP="$1"
    else
        SANITIZED_BUCKET=$(s3_bulk_tool_strip_s3_uri "$1")
        TIMESTAMP=$(s3_bulk_tool_default_timestamp_one_week_ago)
        echo -e "No timestamp provided, defaulting to one week ago: ${INFO}$TIMESTAMP${NC}"
    fi
else
    SANITIZED_BUCKET=$(s3_bulk_tool_strip_s3_uri "$1")
    TIMESTAMP="$2"
fi

if [[ -z "$INPUT_BUCKET" ]]; then
    echo "Error: Input bucket name cannot be empty"
    usage
fi

if [[ -z "$SANITIZED_BUCKET" ]]; then
    echo "Error: Could not derive sanitized bucket name; pass it explicitly as the second argument"
    exit 1
fi

if ! s3_bulk_tool_validate_timestamp "$TIMESTAMP"; then
    echo "Error: Invalid timestamp format. Expected: YYYY-MM-DDTHH:MM:SSZ"
    exit 1
fi

    PREFIX=$(s3_bulk_tool_strip_s3_uri "$PREFIX")
    if [[ "$PREFIX" == s3://* ]]; then
        PREFIX="${PREFIX#s3://}"
        if [[ "$PREFIX" == */* ]]; then
            PREFIX="${PREFIX#*/}"
        fi
    fi

if ! command -v aws &> /dev/null; then
    echo "Error: aws command not found. Please install AWS CLI."
    exit 1
fi

if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${ERR}Error: Not authenticated with AWS. Please configure your AWS credentials.${NC}"
    exit 1
fi

s3_bulk_tool_assume_role_if_needed "$ROLE_ARN" "diff-s3-bulk-files" || exit 1

echo -e "Comparing input ${INFO}s3://$INPUT_BUCKET${NC} → sanitized ${INFO}s3://$SANITIZED_BUCKET${NC}"
echo -e "Input objects modified after ${INFO}$TIMESTAMP${NC}"
if [ -n "$PREFIX" ]; then
    echo -e "Input prefix filter: ${INFO}$PREFIX${NC}"
fi
if [ -n "$INPUT_BASE_PATH" ] || [ -n "$OUTPUT_BASE_PATH" ]; then
    echo -e "Key mapping: strip ${INFO}${INPUT_BASE_PATH:-<none>}${NC}, prepend ${INFO}${OUTPUT_BASE_PATH:-<none>}${NC}"
fi
echo ""

echo -e "NOTE: Requires s3:ListBucket on the input bucket and s3:ListBucket (or s3:HeadObject) on the sanitized bucket. Listing uses AWS CLI only (jq optional)."
echo ""

TIMESTAMP_EPOCH=$(s3_bulk_tool_timestamp_to_epoch "$TIMESTAMP")

CANDIDATES_FILE=$(mktemp)
MISSING_FILE=$(mktemp)
ERROR_FILE=$(mktemp)
trap 'rm -f "$CANDIDATES_FILE" "$MISSING_FILE" "$ERROR_FILE"' EXIT

if ! s3_bulk_tool_list_input_objects_modified_after "$INPUT_BUCKET" "$PREFIX" "$TIMESTAMP_EPOCH" "$CANDIDATES_FILE" "$ERROR_FILE"; then
    echo -e "${ERR}Error: Failed to list objects in input bucket${NC}"
    cat "$ERROR_FILE" >&2
    exit 1
fi

if [ ! -s "$CANDIDATES_FILE" ]; then
    echo -e "${WARN}No input objects found modified after $TIMESTAMP${NC}"
    exit 0
fi

while IFS=$'\t' read -r last_modified input_key; do
    sanitized_key=$(s3_bulk_tool_expected_sanitized_key "$input_key" "$INPUT_BASE_PATH" "$OUTPUT_BASE_PATH")
    if ! s3_bulk_tool_sanitized_object_exists "$SANITIZED_BUCKET" "$sanitized_key"; then
        printf '%s\t%s\n' "$last_modified" "$input_key" >> "$MISSING_FILE"
    fi
done < "$CANDIDATES_FILE"

if [ ! -s "$MISSING_FILE" ]; then
    echo -e "${SUCCESS}All input objects in the time window have a corresponding object in the sanitized bucket.${NC}"
    exit 0
fi

MISSING_COUNT=$(wc -l < "$MISSING_FILE" | tr -d ' ')
echo -e "${WARN}$MISSING_COUNT input object(s) missing from sanitized bucket (newest first):${NC}"
echo ""

# Sort by last_modified descending (ISO timestamps sort lexicographically)
sort -r "$MISSING_FILE" | while IFS=$'\t' read -r last_modified input_key; do
    echo -e "${last_modified}  ${CODE}s3://$INPUT_BUCKET/$input_key${NC}  → expected s3://$SANITIZED_BUCKET/$(s3_bulk_tool_expected_sanitized_key "$input_key" "$INPUT_BASE_PATH" "$OUTPUT_BASE_PATH")"
done

echo ""
echo -e "Tip: Re-trigger processing with ${INFO}replay-s3-writes.sh${NC} on the input bucket (see docs/aws/tools/replay-s3-writes.md)."
exit 1
