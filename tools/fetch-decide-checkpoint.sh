#!/usr/bin/env bash
# Copy the embedded-decide checkpoint into the artifacts bucket.
#
# Layout:
#   {SHARED_RESOURCE_PATH}/decide/strands-decider-2B-qwen3.5-v1-2610/<YYMMDD>/
#     adapter files (strands_decider_config.json, lora/, head.safetensors, tokenizer*)
#     base/   Qwen/Qwen3.5-2B-Base, plus a REVISION file with the pinned commit
#
# Usage:
#   ./tools/fetch-decide-checkpoint.sh <YYMMDD> s3://BUCKET/SHARED_PREFIX/
#   ./tools/fetch-decide-checkpoint.sh <YYMMDD> gs://BUCKET/SHARED_PREFIX/
#
# The bucket path is the date tag. The Hub repo may only publish main; set
# DECIDE_GIT_REVISION to a commit sha to download that snapshot into the date-tag
# prefix. "main" is refused for both the tag and DECIDE_GIT_REVISION.
#
# Requires: huggingface-cli, python3, curl. Upload: aws (S3) or gcloud/gsutil (GCS).

set -euo pipefail

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

MODEL_ID="strands-decider-2B-qwen3.5-v1-2610"
MODEL_REPO="StrandsAgents/${MODEL_ID}"

usage() {
    printf "Usage: %s <YYMMDD> <s3://BUCKET/PREFIX/ | gs://BUCKET/PREFIX/>\n" "$(basename "$0")"
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    usage
    exit 0
fi

if [ "$#" -ne 2 ]; then
    usage >&2
    exit 1
fi

REVISION="$1"
REMOTE_URI="$2"

if [ "${REVISION}" = "main" ]; then
    printf "${ERR}Revision main is refused. Pass a YYMMDD tag.${NC}\n" >&2
    exit 1
fi

if ! [[ "${REVISION}" =~ ^[0-9]{6}$ ]]; then
    printf "${ERR}Revision must be a YYMMDD tag, got '%s'.${NC}\n" "${REVISION}" >&2
    exit 1
fi

SOURCE_REV="${DECIDE_GIT_REVISION:-${REVISION}}"
if [ "${SOURCE_REV}" = "main" ]; then
    printf "${ERR}DECIDE_GIT_REVISION=main is refused. Pin a commit sha.${NC}\n" >&2
    exit 1
fi

if [[ "${REMOTE_URI}" != s3://* && "${REMOTE_URI}" != gs://* ]]; then
    printf "${ERR}Remote destination must be an s3:// or gs:// URI.${NC}\n" >&2
    usage >&2
    exit 1
fi

if ! command -v huggingface-cli >/dev/null 2>&1; then
    printf "${ERR}huggingface-cli is required (pip install -U huggingface_hub).${NC}\n" >&2
    exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
    printf "${ERR}python3 is required to read the adapter config.${NC}\n" >&2
    exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

printf "${INFO}Downloading %s at %s...${NC}\n" "${MODEL_REPO}" "${SOURCE_REV}"
huggingface-cli download "${MODEL_REPO}" --revision "${SOURCE_REV}" --local-dir "${WORKDIR}/adapter"

BASE_MODEL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["base_model"])' \
    "${WORKDIR}/adapter/strands_decider_config.json")"
BASE_REV="$(python3 -c 'import json,sys; value=json.load(open(sys.argv[1])).get("revision"); print("" if value in (None, "null") else value)' \
    "${WORKDIR}/adapter/lora/adapter_config.json")"

if [ -z "${BASE_REV}" ] || [ "${BASE_REV}" = "main" ]; then
    printf "${WARN}Adapter does not pin a base revision. Resolving the current commit of %s.${NC}\n" "${BASE_MODEL}"
    BASE_REV="$(curl -fsSL "https://huggingface.co/api/models/${BASE_MODEL}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')"
fi

if [ -z "${BASE_REV}" ] || [ "${BASE_REV}" = "main" ]; then
    printf "${ERR}Could not pin a base-model commit.${NC}\n" >&2
    exit 1
fi

printf "${INFO}Downloading %s at %s into base/...${NC}\n" "${BASE_MODEL}" "${BASE_REV}"
huggingface-cli download "${BASE_MODEL}" --revision "${BASE_REV}" --local-dir "${WORKDIR}/adapter/base"
printf '%s\n' "${BASE_REV}" > "${WORKDIR}/adapter/base/REVISION"

normalize_prefix() {
    local prefix="$1"
    if [ -n "${prefix}" ] && [ "${prefix: -1}" != "/" ]; then
        prefix="${prefix}/"
    fi
    printf '%s' "${prefix}"
}

DEST_PREFIX="decide/${MODEL_ID}/${REVISION}/"

upload_s3() {
    local uri="$1"
    local path="${uri#s3://}"
    local bucket="${path%%/*}"
    local prefix=""
    if [ "${path}" != "${bucket}" ]; then
        prefix="${path#*/}"
    fi
    prefix="$(normalize_prefix "${prefix}")"
    if ! command -v aws >/dev/null 2>&1; then
        printf "${ERR}aws CLI is required to upload to S3.${NC}\n" >&2
        exit 1
    fi
    printf "${INFO}Uploading to s3://%s/%s%s...${NC}\n" "${bucket}" "${prefix}" "${DEST_PREFIX}"
    aws s3 sync "${WORKDIR}/adapter" "s3://${bucket}/${prefix}${DEST_PREFIX}" --only-show-errors
}

upload_gcs() {
    local uri="$1"
    local path="${uri#gs://}"
    local bucket="${path%%/*}"
    local prefix=""
    if [ "${path}" != "${bucket}" ]; then
        prefix="${path#*/}"
    fi
    prefix="$(normalize_prefix "${prefix}")"
    if command -v gcloud >/dev/null 2>&1; then
        printf "${INFO}Uploading to gs://%s/%s%s...${NC}\n" "${bucket}" "${prefix}" "${DEST_PREFIX}"
        gcloud storage rsync --recursive "${WORKDIR}/adapter" "gs://${bucket}/${prefix}${DEST_PREFIX}"
        return
    fi
    if ! command -v gsutil >/dev/null 2>&1; then
        printf "${ERR}gcloud or gsutil is required to upload to GCS.${NC}\n" >&2
        exit 1
    fi
    printf "${INFO}Uploading to gs://%s/%s%s...${NC}\n" "${bucket}" "${prefix}" "${DEST_PREFIX}"
    gsutil -m rsync -r "${WORKDIR}/adapter" "gs://${bucket}/${prefix}${DEST_PREFIX}"
}

if [[ "${REMOTE_URI}" == s3://* ]]; then
    upload_s3 "${REMOTE_URI}"
else
    upload_gcs "${REMOTE_URI}"
fi

printf "${SUCCESS}Checkpoint uploaded under %s%s${NC}\n" "${REMOTE_URI}" "${DEST_PREFIX}"
