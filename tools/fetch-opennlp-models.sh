#!/usr/bin/env bash
# Downloads Apache OpenNLP model binaries for local dev, tests, and CI.
# Optionally uploads runtime models to a remote bucket for cloud deployments.
#
# Usage:
#   ./tools/fetch-opennlp-models.sh
#   ./tools/fetch-opennlp-models.sh s3://BUCKET/SHARED_RESOURCE_PATH/
#   ./tools/fetch-opennlp-models.sh gs://BUCKET/SHARED_RESOURCE_PATH/
#
# Requires: curl. For upload: aws (S3) or gsutil (GCS).

set -euo pipefail

# Use semantic colors dynamically based on terminal capability
if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
    ERR=$(tput setaf 1)
    SUCCESS=$(tput setaf 2)
    WARN=$(tput setaf 3)
    INFO=$(tput setaf 4)
    NC=$(tput sgr0)
else
    ERR='\033[0;31m'
    SUCCESS='\033[0;32m'
    WARN='\033[1;33m'
    INFO='\033[0;34m'
    NC='\033[0m'
fi

usage() {
    printf "Usage: %s [s3://BUCKET/PREFIX/ | gs://BUCKET/PREFIX/]\n" "$(basename "$0")"
    printf "  With no arguments, downloads models to java/gateway-core/src/main/resources/opennlp/\n"
    printf "  With a bucket URI, also uploads runtime models to PREFIX/opennlp/ in the bucket.\n"
}

REMOTE_URI="${1:-}"

if [ "${REMOTE_URI}" = "-h" ] || [ "${REMOTE_URI}" = "--help" ]; then
    usage
    exit 0
fi

if [ -n "${REMOTE_URI}" ] && [[ "${REMOTE_URI}" != s3://* && "${REMOTE_URI}" != gs://* ]]; then
    printf "${ERR}Remote destination must be an s3:// or gs:// URI.${NC}\n" >&2
    usage >&2
    exit 1
fi

BASE_URL="https://opennlp.sourceforge.net/models-1.5"
DEST_DIR="java/gateway-core/src/main/resources/opennlp"

# Downloaded for local dev / tests (includes NER models not used by sentenceMetadata at runtime).
MODELS=(
    "en-sent.bin"
    "en-pos-maxent.bin"
    "en-chunker.bin"
    "en-ner-person.bin"
    "en-ner-location.bin"
    "en-ner-organization.bin"
    "en-ner-date.bin"
)

# Required by SentenceMetadataProcessor at opennlp/{model}.bin via ResourceService.
RUNTIME_MODELS=(
    "en-sent.bin"
    "en-pos-maxent.bin"
    "en-chunker.bin"
)

# Move to the project root
cd "$(dirname "$0")/.."

mkdir -p "$DEST_DIR"

printf "${INFO}Downloading Apache OpenNLP models to %s...${NC}\n" "$DEST_DIR"

for MODEL in "${MODELS[@]}"; do
    TARGET_PATH="$DEST_DIR/$MODEL"
    if [ -s "$TARGET_PATH" ]; then
        printf "${WARN}Skipping %s (already exists and not empty)${NC}\n" "$MODEL"
        continue
    fi

    printf "${INFO}Fetching %s...${NC}\n" "$MODEL"
    if curl -L -f -s -o "$TARGET_PATH" "$BASE_URL/$MODEL"; then
        printf "${SUCCESS}Successfully downloaded %s${NC}\n" "$MODEL"
    else
        printf "${ERR}Failed to download %s${NC}\n" "$MODEL"
        rm -f "$TARGET_PATH"
        exit 1
    fi
done

printf "${SUCCESS}All required OpenNLP models downloaded successfully.${NC}\n"

if [ -z "${REMOTE_URI}" ]; then
    exit 0
fi

normalize_prefix() {
    local prefix="$1"
    if [ -n "$prefix" ] && [ "${prefix: -1}" != "/" ]; then
        prefix="${prefix}/"
    fi
    printf '%s' "$prefix"
}

upload_to_s3() {
    local uri="$1"
    local path="${uri#s3://}"
    local bucket="${path%%/*}"
    local prefix=""

    if [ "$path" != "$bucket" ]; then
        prefix="${path#*/}"
    fi
    prefix="$(normalize_prefix "$prefix")"

    if ! command -v aws >/dev/null 2>&1; then
        printf "${ERR}aws CLI is required to upload to S3.${NC}\n" >&2
        exit 1
    fi

    printf "${INFO}Uploading runtime OpenNLP models to s3://%s/%sopennlp/...${NC}\n" "$bucket" "$prefix"

    for MODEL in "${RUNTIME_MODELS[@]}"; do
        local source_path="$DEST_DIR/$MODEL"
        local object_key="${prefix}opennlp/${MODEL}"
        if [ ! -s "$source_path" ]; then
            printf "${ERR}Missing local model file: %s${NC}\n" "$source_path" >&2
            exit 1
        fi
        printf "${INFO}Uploading %s...${NC}\n" "$MODEL"
        aws s3 cp "$source_path" "s3://${bucket}/${object_key}"
    done
}

upload_to_gcs() {
    local uri="$1"
    local path="${uri#gs://}"
    local bucket="${path%%/*}"
    local prefix=""

    if [ "$path" != "$bucket" ]; then
        prefix="${path#*/}"
    fi
    prefix="$(normalize_prefix "$prefix")"

    if ! command -v gsutil >/dev/null 2>&1; then
        printf "${ERR}gsutil is required to upload to GCS.${NC}\n" >&2
        exit 1
    fi

    printf "${INFO}Uploading runtime OpenNLP models to gs://%s/%sopennlp/...${NC}\n" "$bucket" "$prefix"

    for MODEL in "${RUNTIME_MODELS[@]}"; do
        local source_path="$DEST_DIR/$MODEL"
        local object_name="${prefix}opennlp/${MODEL}"
        if [ ! -s "$source_path" ]; then
            printf "${ERR}Missing local model file: %s${NC}\n" "$source_path" >&2
            exit 1
        fi
        printf "${INFO}Uploading %s...${NC}\n" "$MODEL"
        gsutil cp "$source_path" "gs://${bucket}/${object_name}"
    done
}

if [[ "${REMOTE_URI}" == s3://* ]]; then
    upload_to_s3 "${REMOTE_URI}"
elif [[ "${REMOTE_URI}" == gs://* ]]; then
    upload_to_gcs "${REMOTE_URI}"
fi

printf "${SUCCESS}OpenNLP runtime models uploaded to %s${NC}\n" "$REMOTE_URI"
