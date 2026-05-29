#!/bin/bash

# Verify AWS/GCP release bundles for a GitHub release.
#
# Checks:
#   - artifacts exist in S3/GCS
#   - sha256 object metadata is present
#   - downloaded content matches metadata sha256
#   - AWS regions agree on sha256 metadata
#   - compiled classes do not contain Eclipse "Unresolved compilation problem" stubs
#   - expected platform entrypoint classes are present
#
# Usage:
#   ./tools/release/verify-bundles.sh [RELEASE_TAG] [--rc] [--aws-only|--gcp-only]
#
# Examples:
#   ./tools/release/verify-bundles.sh                 # latest GitHub release
#   ./tools/release/verify-bundles.sh v0.6.1
#   ./tools/release/verify-bundles.sh v0.6.2 --rc
#
# Required tools:
#   gh, aws, curl, jar, unzip, strings, shasum|sha256sum|openssl
# Optional:
#   gsutil (falls back to public HTTP for GCS download/metadata)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COLORSCHEME_SH="${SCRIPT_DIR}/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=/dev/null
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=lib/verify-bundles-lib.sh
source "${SCRIPT_DIR}/lib/verify-bundles-lib.sh"

AWS_BUCKET_PREFIX="${AWS_BUCKET_PREFIX:-psoxy-public-artifacts}"
GCS_BUCKET="${GCS_BUCKET:-psoxy-public-artifacts}"
if [ -n "${AWS_REGIONS:-}" ]; then
  IFS=',' read -ra AWS_REGIONS <<< "$AWS_REGIONS"
else
  AWS_REGIONS=(us-east-1 us-east-2 us-west-1 us-west-2)
fi

REQUESTED_RELEASE=""
IS_RC="false"
CHECK_AWS="true"
CHECK_GCP="true"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rc)
      IS_RC="true"
      shift
      ;;
    --aws-only)
      CHECK_GCP="false"
      shift
      ;;
    --gcp-only)
      CHECK_AWS="false"
      shift
      ;;
    -h|--help)
      sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    v*)
      REQUESTED_RELEASE="$1"
      shift
      ;;
    [0-9]*)
      REQUESTED_RELEASE="v$1"
      shift
      ;;
    *)
      echo -e "${ERR}Error: Unknown argument: $1${NC}"
      echo "Usage: $0 [RELEASE_TAG] [--rc] [--aws-only|--gcp-only]"
      exit 1
      ;;
  esac
done

for cmd in gh curl jar unzip strings; do
  verify_bundles_require_cmd "$cmd"
done
if [ "$CHECK_AWS" = "true" ]; then
  verify_bundles_require_cmd "aws"
fi
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1 && ! command -v openssl >/dev/null 2>&1; then
  echo -e "${ERR}Error: one of sha256sum, shasum, or openssl is required.${NC}"
  exit 1
fi

if [ "$CHECK_AWS" = "true" ]; then
  if ! aws sts get-caller-identity >/dev/null 2>&1; then
    echo -e "${ERR}Error: AWS CLI is not configured/authenticated.${NC}"
    echo -e "${WARN}AWS bundle verification requires credentials with s3:GetObject and s3:HeadObject on psoxy-public-artifacts-* buckets.${NC}"
    exit 1
  fi
fi

RELEASE_TAG="$(verify_bundles_resolve_release_tag "$REQUESTED_RELEASE")"
VERSION="$(verify_bundles_version_from_tag "$RELEASE_TAG")"
verify_bundles_artifact_names "$VERSION" "$IS_RC"

VERIFY_TMP_BASE="${TMPDIR:-/tmp}"
if [ ! -d "$VERIFY_TMP_BASE" ]; then
  VERIFY_TMP_BASE="/tmp"
fi
TMPDIR="$(mktemp -d "${VERIFY_TMP_BASE%/}/psoxy-verify-bundles.XXXXXX")"
cleanup() {
  rm -rf "$TMPDIR"
}
trap cleanup EXIT

echo -e "${INFO}=== Verifying release bundles ===${NC}"
echo -e "${INFO}Release tag:${NC} ${SUCCESS}${RELEASE_TAG}${NC}"
echo -e "${INFO}Version:${NC} ${SUCCESS}${VERSION}${NC}"
echo -e "${INFO}AWS artifact:${NC} ${CODE}${AWS_ARTIFACT}${NC}"
echo -e "${INFO}GCP artifact:${NC} ${CODE}${GCP_ARTIFACT}${NC}"
if [ "$IS_RC" = "true" ]; then
  echo -e "${INFO}Mode:${NC} ${WARN}RC artifact names${NC}"
fi
echo ""

AWS_OK="true"
GCP_OK="true"

if [ "$CHECK_AWS" = "true" ]; then
  if verify_bundles_verify_aws_bundle "$VERSION" "$TMPDIR"; then
    echo -e "${SUCCESS}AWS bundle verification passed.${NC}"
  else
    AWS_OK="false"
    echo -e "${ERR}AWS bundle verification failed.${NC}"
  fi
  echo ""
fi

if [ "$CHECK_GCP" = "true" ]; then
  if verify_bundles_verify_gcp_bundle "$VERSION" "$TMPDIR"; then
    echo -e "${SUCCESS}GCP bundle verification passed.${NC}"
  else
    GCP_OK="false"
    echo -e "${ERR}GCP bundle verification failed.${NC}"
  fi
  echo ""
fi

if [ "$AWS_OK" = "true" ] && [ "$GCP_OK" = "true" ]; then
  echo -e "${SUCCESS}✓ All requested bundle checks passed for ${RELEASE_TAG}.${NC}"
  exit 0
fi

echo -e "${ERR}✗ Bundle verification failed for ${RELEASE_TAG}.${NC}"
exit 1
