#!/bin/bash

# List objects present in a bulk connector -input bucket but missing from the matching -sanitized
# bucket (within a time window). Useful when debugging bulk processing failures.
#
# Usage: ./diff-gcs-bulk-files.sh [options] <input_bucket> [<sanitized_bucket>] [timestamp]

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
# shellcheck source=../set-term-colorscheme.sh
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=gcs-bulk-tool-common.sh
source "$(dirname "$0")/gcs-bulk-tool-common.sh"

usage() {
    echo "Usage: $0 [--prefix PREFIX] [--input-base-path PATH] [--output-base-path PATH] <input_bucket> [<sanitized_bucket>] [timestamp]"
    echo ""
    echo "Options:"
    echo "  --prefix PREFIX         List only input objects whose keys start with this prefix"
    echo "  --input-base-path PATH  Strip this prefix from input keys when computing expected sanitized keys"
    echo "  --output-base-path PATH Prepend this prefix to sanitized keys"
    echo ""
    echo "Arguments:"
    echo "  input_bucket     Bulk connector input bucket (with or without gs:// prefix)"
    echo "  sanitized_bucket Optional. Defaults to input name with trailing -input replaced by -sanitized"
    echo "  timestamp        Optional ISO 8601 time; only input objects updated after this time are checked"
    echo ""
    echo "Output:"
    echo "  Missing objects on stdout, newest first"
    exit 1
}

PREFIX=""
INPUT_BASE_PATH=""
OUTPUT_BASE_PATH=""

while [ $# -gt 0 ]; do
    case "$1" in
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

INPUT_BUCKET=$(gcs_bulk_tool_strip_gs_uri "$1")
shift

SANITIZED_BUCKET=""
TIMESTAMP=""

if [ $# -eq 0 ]; then
    SANITIZED_BUCKET=$(gcs_bulk_tool_derive_sanitized_bucket "$INPUT_BUCKET")
    TIMESTAMP=$(gcs_bulk_tool_default_timestamp_one_week_ago)
    echo -e "No timestamp provided, defaulting to one week ago: ${INFO}$TIMESTAMP${NC}"
elif [ $# -eq 1 ]; then
    if gcs_bulk_tool_validate_timestamp "$1"; then
        SANITIZED_BUCKET=$(gcs_bulk_tool_derive_sanitized_bucket "$INPUT_BUCKET")
        TIMESTAMP="$1"
    else
        SANITIZED_BUCKET=$(gcs_bulk_tool_strip_gs_uri "$1")
        TIMESTAMP=$(gcs_bulk_tool_default_timestamp_one_week_ago)
        echo -e "No timestamp provided, defaulting to one week ago: ${INFO}$TIMESTAMP${NC}"
    fi
else
    SANITIZED_BUCKET=$(gcs_bulk_tool_strip_gs_uri "$1")
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

if ! gcs_bulk_tool_validate_timestamp "$TIMESTAMP"; then
    echo "Error: Invalid timestamp format. Expected: YYYY-MM-DDTHH:MM:SSZ"
    exit 1
fi

PREFIX=$(gcs_bulk_tool_strip_gs_uri "$PREFIX")
if [[ "$PREFIX" == "$INPUT_BUCKET"/* ]]; then
    PREFIX="${PREFIX#"$INPUT_BUCKET"/}"
elif [[ "$PREFIX" == "$INPUT_BUCKET" ]]; then
    PREFIX=""
fi
if [ -n "$PREFIX" ] && [[ "$PREFIX" != */ ]]; then
    PREFIX="${PREFIX}/"
fi

if ! command -v gsutil &> /dev/null; then
    echo "Error: gsutil command not found. Please install Google Cloud SDK."
    exit 1
fi

if ! gsutil ls &> /dev/null; then
    echo -e "${ERR}Error: Not authenticated with Google Cloud. Please run 'gcloud auth login'.${NC}"
    exit 1
fi

echo -e "Comparing input ${INFO}gs://$INPUT_BUCKET${NC} → sanitized ${INFO}gs://$SANITIZED_BUCKET${NC}"
echo -e "Input objects updated after ${INFO}$TIMESTAMP${NC}"
if [ -n "$PREFIX" ]; then
    echo -e "Input prefix filter: ${INFO}$PREFIX${NC}"
fi
echo ""

echo -e "NOTE: Requires storage.objects.list on the input bucket and storage.objects.get on the sanitized bucket."
echo -e "${INFO}roles/storage.objectViewer${NC} on both buckets is sufficient."
echo ""

TIMESTAMP_EPOCH=$(gcs_bulk_tool_timestamp_to_epoch "$TIMESTAMP")

CANDIDATES_FILE=$(mktemp)
MISSING_FILE=$(mktemp)
trap 'rm -f "$CANDIDATES_FILE" "$MISSING_FILE"' EXIT

gcs_bulk_tool_list_input_objects_modified_after "$INPUT_BUCKET" "$PREFIX" "$TIMESTAMP_EPOCH" "$CANDIDATES_FILE"

if [ ! -s "$CANDIDATES_FILE" ]; then
    echo -e "${WARN}No input objects found updated after $TIMESTAMP${NC}"
    exit 0
fi

while IFS=$'\t' read -r updated_time input_key; do
    sanitized_key=$(gcs_bulk_tool_expected_sanitized_key "$input_key" "$INPUT_BASE_PATH" "$OUTPUT_BASE_PATH")
    if ! gcs_bulk_tool_sanitized_object_exists "$SANITIZED_BUCKET" "$sanitized_key"; then
        printf '%s\t%s\n' "$updated_time" "$input_key" >> "$MISSING_FILE"
    fi
done < "$CANDIDATES_FILE"

if [ ! -s "$MISSING_FILE" ]; then
    echo -e "${SUCCESS}All input objects in the time window have a corresponding object in the sanitized bucket.${NC}"
    exit 0
fi

MISSING_COUNT=$(wc -l < "$MISSING_FILE" | tr -d ' ')
echo -e "${WARN}$MISSING_COUNT input object(s) missing from sanitized bucket (newest first):${NC}"
echo ""

sort -r "$MISSING_FILE" | while IFS=$'\t' read -r updated_time input_key; do
    expected=$(gcs_bulk_tool_expected_sanitized_key "$input_key" "$INPUT_BASE_PATH" "$OUTPUT_BASE_PATH")
    echo -e "${updated_time}  ${CODE}gs://$INPUT_BUCKET/$input_key${NC}  → expected gs://$SANITIZED_BUCKET/$expected"
done

echo ""
echo -e "Tip: Re-trigger processing with ${INFO}replay-gcs-writes.sh${NC} (see docs/gcp/tools/replay-gcs-writes.md)."
exit 1
