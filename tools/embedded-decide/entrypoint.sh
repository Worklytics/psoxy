#!/bin/sh
# Container entrypoint for CLASSIFY_IMPLEMENTATION=embedded-decide.
# Copies the checkpoint, starts strands-decider on 127.0.0.1:8000, waits until
# health succeeds, then execs the process arguments (Lambda: /lambda-entrypoint.sh).
# A failed copy or health check exits before the Java handler starts. There is
# no fall-through to remote-llm.
set -eu

PORT=8000
MODEL_ID="${CLASSIFY_MODEL:-strands-decider-2B-qwen3.5-v1-2610}"
REVISION="${CLASSIFY_MODEL_REVISION:?CLASSIFY_MODEL_REVISION is required}"
DEST="${DECIDE_CHECKPOINT_DIR:-/opt/decide/checkpoint}"
HF_HOME="${HF_HOME:-/opt/decide/hf}"
export HF_HOME
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1

if [ "${REVISION}" = "main" ]; then
    echo "CLASSIFY_MODEL_REVISION=main is refused" >&2
    exit 1
fi

mkdir -p "${DEST}" "${HF_HOME}"

if [ -n "${DECIDE_CHECKPOINT_LOCAL:-}" ]; then
    if [ "${DECIDE_CHECKPOINT_LOCAL}" != "${DEST}" ]; then
        cp -a "${DECIDE_CHECKPOINT_LOCAL}/." "${DEST}/"
    fi
else
    BUCKET="${REMOTE_RESOURCE_BUCKET:?REMOTE_RESOURCE_BUCKET is required}"
    SHARED="${SHARED_RESOURCE_PATH:-}"
    case "${SHARED}" in
        */) ;;
        "") ;;
        *) SHARED="${SHARED}/" ;;
    esac
    PREFIX="${SHARED}decide/${MODEL_ID}/${REVISION}/"
    if command -v aws >/dev/null 2>&1; then
        aws s3 sync "s3://${BUCKET}/${PREFIX}" "${DEST}" --only-show-errors
    elif command -v gcloud >/dev/null 2>&1; then
        gcloud storage rsync --recursive "gs://${BUCKET}/${PREFIX}" "${DEST}"
    else
        echo "embedded-decide needs aws or gcloud to copy s3/gs://${BUCKET}/${PREFIX}" >&2
        exit 1
    fi
fi

if [ ! -f "${DEST}/strands_decider_config.json" ] || [ ! -d "${DEST}/base" ]; then
    echo "embedded-decide checkpoint is missing under ${DEST}" >&2
    exit 1
fi

BASE_REV="$(cat "${DEST}/base/REVISION")"
CACHE="${HF_HOME}/hub/models--Qwen--Qwen3.5-2B-Base"
mkdir -p "${CACHE}/snapshots" "${CACHE}/refs"
ln -sfn "${DEST}/base" "${CACHE}/snapshots/${BASE_REV}"
printf '%s\n' "${BASE_REV}" > "${CACHE}/refs/main"

strands-decider serve "${DEST}" --device cpu --port "${PORT}" &
SERVER_PID=$!

ready=0
i=0
while [ "${i}" -lt 90 ]; do
    if python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:${PORT}/health', timeout=2)" >/dev/null 2>&1; then
        ready=1
        break
    fi
    if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
        echo "strands-decider exited before health succeeded" >&2
        exit 1
    fi
    i=$((i + 1))
    sleep 2
done

if [ "${ready}" -ne 1 ]; then
    echo "embedded-decide health check timed out" >&2
    kill "${SERVER_PID}" 2>/dev/null || true
    exit 1
fi

if [ "$#" -eq 0 ]; then
    exec /lambda-entrypoint.sh
fi
exec "$@"
