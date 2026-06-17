#!/bin/bash

# Build markdown blocks for deployment bundle references in GitHub release notes.
#
# Usage:
#   ./deployment-bundle-release-notes-block.sh aws <tag> <sha256> <artifact-uris.txt>
#   ./deployment-bundle-release-notes-block.sh gcp <tag> <sha256> <artifact_uri>
#
# Writes block content to stdout (includes start/end HTML comment markers).

set -euo pipefail

PLATFORM="${1:?platform required (aws|gcp)}"
TAG_NAME="${2:?tag required}"
SHA256="${3:?sha256 required}"
ARTIFACTS_INPUT="${4:?artifact input required}"

VERSION="${TAG_NAME#v}"
UPGRADE_GUIDE_URL="https://github.com/Worklytics/psoxy/blob/main/docs/guides/upgrading-versions.md"
BUCKET_PREFIX="${BUCKET_PREFIX:-psoxy-public-artifacts}"
GCS_BUCKET="${GCS_BUCKET:-psoxy-public-artifacts}"

usage_guidance() {
  local platform="$1"
  local example=""
  case "$platform" in
    aws)
      example="deployment_bundle = \"s3://${BUCKET_PREFIX}-us-east-1/psoxy-aws-${VERSION}.jar\""
      ;;
    gcp)
      example="deployment_bundle = \"gs://${GCS_BUCKET}/psoxy-gcp-${VERSION}.zip\""
      ;;
  esac
  cat <<EOF

To use a deployment bundle in Terraform, set \`deployment_bundle\` to the S3 or GCS URI for your platform, for example:

\`\`\`hcl
${example}
\`\`\`

To upgrade to this release using one of our examples, run \`./upgrade-terraform-modules ${TAG_NAME}\`. See [Upgrading Proxy Versions](${UPGRADE_GUIDE_URL}) for more information.
EOF
}

case "$PLATFORM" in
  aws)
    IDENTIFIER="<!-- aws-artifacts-info -->"
    printf '%s\n' "$IDENTIFIER"
    printf '## AWS Deployment Bundles\n\n'
    printf 'SHA256: `%s`\n\n' "$SHA256"
    while IFS='=' read -r key url; do
      [ -z "$key" ] && continue
      region="${key#ARTIFACT_URI_}"
      filename="${url##*/}"
      bucket="${BUCKET_PREFIX}-${region}"
      s3_uri="s3://${bucket}/${filename}"
      printf '* %s:\n' "$region"
      printf '  * S3 URI: `%s`\n' "$s3_uri"
      printf '  * HTTP URL: [%s](%s)\n' "$url" "$url"
    done < "$ARTIFACTS_INPUT"
    usage_guidance aws
    printf '%s\n' "$IDENTIFIER"
    ;;
  gcp)
    IDENTIFIER="<!-- gcp-artifacts-info -->"
    ARTIFACT_URI="$ARTIFACTS_INPUT"
    HTTP_URL="$(printf '%s' "$ARTIFACT_URI" | sed 's|^gs://|https://storage.googleapis.com/|')"
    printf '%s\n' "$IDENTIFIER"
    printf '## GCP Deployment Bundle\n\n'
    printf 'GCS URI: `%s`\n' "$ARTIFACT_URI"
    printf 'HTTP URL: [%s](%s)\n' "$HTTP_URL" "$HTTP_URL"
    printf 'SHA256: `%s`\n' "$SHA256"
    usage_guidance gcp
    printf '%s\n' "$IDENTIFIER"
    ;;
  *)
    printf 'Unknown platform: %s\n' "$PLATFORM" >&2
    exit 1
    ;;
esac
