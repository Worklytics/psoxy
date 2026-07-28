#!/usr/bin/env bash
# Downloads a Jlama-compatible HuggingFace model (SafeTensors layout), zips it for remote loading,
# and optionally uploads the archive to a remote bucket for cloud genMetadata deployments.
#
# Usage:
#   ./tools/fetch-gen-metadata-model.sh
#   ./tools/fetch-gen-metadata-model.sh s3://BUCKET/SHARED_RESOURCE_PATH/ [MODEL_ID]
#   ./tools/fetch-gen-metadata-model.sh gs://BUCKET/SHARED_RESOURCE_PATH/ [MODEL_ID]
#   ./tools/fetch-gen-metadata-model.sh --from-dir /path/to/model-dir s3://BUCKET/PREFIX/ [MODEL_ID]
#
# MODEL_ID defaults to PSOXY_GEN_MODEL or tjake/Llama-3.2-1B-Instruct-JQ4.
# Upload path: PREFIX/llm/{MODEL_ID with / replaced by __}.zip
#
# Requires: zip. For download: huggingface-cli (pip install huggingface_hub). For upload: aws or gsutil.

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

DEFAULT_MODEL_ID="tjake/Llama-3.2-1B-Instruct-JQ4"
FROM_DIR=""
REMOTE_URI=""
MODEL_ID="${PSOXY_GEN_MODEL:-$DEFAULT_MODEL_ID}"

usage() {
    printf "Usage: %s [--from-dir DIR] [s3://BUCKET/PREFIX/ | gs://BUCKET/PREFIX/] [MODEL_ID]\n" "$(basename "$0")"
    printf "  Downloads (or uses --from-dir), zips to .build/gen-metadata-models/llm/, optionally uploads.\n"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --from-dir)
            shift
            FROM_DIR="${1:?--from-dir requires a path}"
            shift
            ;;
        s3://*|gs://*)
            REMOTE_URI="$1"
            shift
            ;;
        *)
            if [ -z "${REMOTE_URI}" ] && [[ "$1" == s3://* || "$1" == gs://* ]]; then
                REMOTE_URI="$1"
            else
                MODEL_ID="$1"
            fi
            shift
            ;;
    esac
done

if [ -n "${REMOTE_URI}" ] && [[ "${REMOTE_URI}" != s3://* && "${REMOTE_URI}" != gs://* ]]; then
    printf "${ERR}Remote destination must be an s3:// or gs:// URI.${NC}\n" >&2
    usage >&2
    exit 1
fi

archive_stem="${MODEL_ID//\//__}"
archive_name="${archive_stem}.zip"

cd "$(dirname "$0")/.."
DEST_ROOT=".build/gen-metadata-models"
MODEL_DIR="${DEST_ROOT}/${archive_stem}"
ARCHIVE_PATH="${DEST_ROOT}/llm/${archive_name}"

mkdir -p "${DEST_ROOT}/llm"

if [ -n "${FROM_DIR}" ]; then
    if [ ! -f "${FROM_DIR}/config.json" ]; then
        printf "${ERR}--from-dir must contain config.json at its root (Jlama SafeTensors layout).${NC}\n" >&2
        exit 1
    fi
    rm -rf "${MODEL_DIR}"
    cp -R "${FROM_DIR}" "${MODEL_DIR}"
else
    if [ -f "${MODEL_DIR}/config.json" ]; then
        printf "${WARN}Using existing model directory %s${NC}\n" "${MODEL_DIR}"
    else
        if ! command -v huggingface-cli >/dev/null 2>&1; then
            printf "${ERR}huggingface-cli not found. Install with: pip install huggingface_hub${NC}\n" >&2
            printf "${ERR}Or pass --from-dir pointing at a local SafeTensors model directory.${NC}\n" >&2
            exit 1
        fi
        rm -rf "${MODEL_DIR}"
        mkdir -p "${MODEL_DIR}"
        printf "${INFO}Downloading %s from HuggingFace to %s...${NC}\n" "${MODEL_ID}" "${MODEL_DIR}"
        huggingface-cli download "${MODEL_ID}" --local-dir "${MODEL_DIR}"
    fi
fi

if [ ! -f "${MODEL_DIR}/config.json" ]; then
    printf "${ERR}Model directory missing config.json: %s${NC}\n" "${MODEL_DIR}" >&2
    exit 1
fi

ARCHIVE_ABS="$(cd "${DEST_ROOT}/llm" && pwd)/${archive_name}"
printf "${INFO}Creating archive %s...${NC}\n" "${ARCHIVE_ABS}"
rm -f "${ARCHIVE_ABS}"
(
    cd "${MODEL_DIR}"
    zip -qr "${ARCHIVE_ABS}" .
)

printf "${SUCCESS}Created %s${NC}\n" "${ARCHIVE_ABS}"
ARCHIVE_PATH="${ARCHIVE_ABS}"

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

    local object_key="${prefix}llm/${archive_name}"
    printf "${INFO}Uploading to s3://%s/%s...${NC}\n" "$bucket" "$object_key"
    aws s3 cp "${ARCHIVE_PATH}" "s3://${bucket}/${object_key}"
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

    local object_name="${prefix}llm/${archive_name}"
    printf "${INFO}Uploading to gs://%s/%s...${NC}\n" "$bucket" "$object_name"
    gsutil cp "${ARCHIVE_PATH}" "gs://${bucket}/${object_name}"
}

if [[ "${REMOTE_URI}" == s3://* ]]; then
    upload_to_s3 "${REMOTE_URI}"
elif [[ "${REMOTE_URI}" == gs://* ]]; then
    upload_to_gcs "${REMOTE_URI}"
fi

printf "${SUCCESS}genMetadata model archive uploaded to %s${NC}\n" "${REMOTE_URI}"
