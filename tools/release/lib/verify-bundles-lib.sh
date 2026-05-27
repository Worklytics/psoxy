#!/bin/bash

# Shared helpers for release bundle verification.
# Sourced by tools/release/verify-bundles.sh; do not run directly.

verify_bundles_require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${ERR}Error: ${cmd} is required but not installed.${NC}"
    exit 1
  fi
}

verify_bundles_compute_sha256() {
  local file_path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file_path" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file_path" | awk '{print $1}'
  else
    openssl dgst -sha256 "$file_path" | awk '{print $NF}'
  fi
}

verify_bundles_normalize_tag() {
  local tag="$1"
  if [[ "$tag" != v* ]]; then
    tag="v${tag}"
  fi
  printf '%s' "$tag"
}

verify_bundles_version_from_tag() {
  local tag
  tag="$(verify_bundles_normalize_tag "$1")"
  if [[ ! "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.+-]+)?)$ ]]; then
    echo -e "${ERR}Error: Unsupported release tag format: ${tag}${NC}" >&2
    echo -e "${WARN}Expected tags like v0.6.1 or v0.6.2-rc1${NC}" >&2
    return 1
  fi
  printf '%s' "${BASH_REMATCH[1]}"
}

verify_bundles_artifact_names() {
  local version="$1"
  local is_rc="$2"

  if [ "$is_rc" = "true" ]; then
    AWS_ARTIFACT="psoxy-aws-${version}-rc.jar"
    GCP_ARTIFACT="psoxy-gcp-${version}-rc.zip"
  else
    AWS_ARTIFACT="psoxy-aws-${version}.jar"
    GCP_ARTIFACT="psoxy-gcp-${version}.zip"
  fi
}

verify_bundles_resolve_release_tag() {
  local requested_tag="${1:-}"

  if [ -n "$requested_tag" ]; then
    verify_bundles_normalize_tag "$requested_tag"
    return 0
  fi

  local latest_tag
  latest_tag="$(gh release list --exclude-drafts --limit 1 --json tagName -q '.[0].tagName' 2>/dev/null || true)"
  if [ -z "$latest_tag" ] || [ "$latest_tag" = "null" ]; then
    echo -e "${ERR}Error: Could not determine latest GitHub release.${NC}" >&2
    echo -e "${WARN}Pass an explicit release tag, e.g. v0.6.1${NC}" >&2
    return 1
  fi

  printf '%s' "$latest_tag"
}

verify_bundles_jar_from_artifact() {
  local artifact_path="$1"
  local artifact_kind="$2"
  local jar_path="$3"

  case "$artifact_kind" in
    jar)
      cp "$artifact_path" "$jar_path"
      ;;
    zip)
      local inner_jar
      inner_jar="$(unzip -Z1 "$artifact_path" | grep -E '^psoxy-.*\.jar$' | head -1)"
      if [ -z "$inner_jar" ]; then
        echo -e "${ERR}Error: No psoxy-*.jar found inside ${artifact_path}${NC}"
        return 1
      fi
      unzip -p "$artifact_path" "$inner_jar" > "$jar_path"
      ;;
    *)
      echo -e "${ERR}Error: Unknown artifact kind: ${artifact_kind}${NC}"
      return 1
      ;;
  esac
}

verify_bundles_platform_label() {
  case "$1" in
    aws) printf 'AWS' ;;
    gcp) printf 'GCP' ;;
    *) printf '%s' "$1" ;;
  esac
}

verify_bundles_scan_jar_for_compilation_errors() {
  local jar_path="$1"
  local label="$2"
  local bad_classes=()
  local classfile

  while IFS= read -r classfile; do
    if unzip -p "$jar_path" "$classfile" | strings | grep -qF "Unresolved compilation problem"; then
      bad_classes+=("$classfile")
    fi
  done < <(jar tf "$jar_path" | grep -E '\.class$' | grep -E '^(co/worklytics|com/avaulta)/')

  if [ "${#bad_classes[@]}" -gt 0 ]; then
    echo -e "${ERR}✗ ${label}: found ${#bad_classes[@]} class file(s) with unresolved compilation problems${NC}"
    local sample_count="${#bad_classes[@]}"
    if [ "$sample_count" -gt 10 ]; then
      sample_count=10
    fi
    local i
    for ((i=0; i<sample_count; i++)); do
      echo -e "  ${ERR}${bad_classes[$i]}${NC}"
    done
    if [ "${#bad_classes[@]}" -gt 10 ]; then
      echo -e "  ${WARN}... and $((${#bad_classes[@]} - 10)) more${NC}"
    fi
    return 1
  fi

  echo -e "${SUCCESS}✓ ${label}: no unresolved compilation problems in project classes${NC}"
  return 0
}

verify_bundles_check_jar_structure() {
  local jar_path="$1"
  local platform="$2"
  local platform_label
  platform_label="$(verify_bundles_platform_label "$platform")"
  local missing=()

  case "$platform" in
    aws)
      if ! jar tf "$jar_path" | grep -Fxq "co/worklytics/psoxy/aws/DaggerAwsContainer.class"; then
        missing+=("co/worklytics/psoxy/aws/DaggerAwsContainer.class")
      fi
      ;;
    gcp)
      if ! jar tf "$jar_path" | grep -Fxq "co/worklytics/psoxy/DaggerGcpContainer.class"; then
        missing+=("co/worklytics/psoxy/DaggerGcpContainer.class")
      fi
      ;;
  esac

  if ! jar tf "$jar_path" | grep -Fq 'THIRD-PARTY-LICENSE-LIST.txt'; then
    missing+=("THIRD-PARTY-LICENSE-LIST.txt")
  fi

  if [ "${#missing[@]}" -gt 0 ]; then
    echo -e "${ERR}✗ ${platform_label} JAR missing expected bundle contents:${NC}"
    local entry
    for entry in "${missing[@]}"; do
      echo -e "  ${ERR}${entry}${NC}"
    done
    return 1
  fi

  echo -e "${SUCCESS}✓ ${platform_label} JAR contains Dagger container and license artifacts${NC}"
  return 0
}

verify_bundles_check_entrypoint_classes() {
  local jar_path="$1"
  local platform="$2"
  local platform_label
  platform_label="$(verify_bundles_platform_label "$platform")"
  local entrypoints=()

  case "$platform" in
    aws)
      entrypoints=(
        "co/worklytics/psoxy/AwsApiGatewayV2ApiDataRequestHandler.class"
        "co/worklytics/psoxy/S3Handler.class"
      )
      ;;
    gcp)
      entrypoints=(
        "co/worklytics/psoxy/GCSFileEvent.class"
        "co/worklytics/psoxy/Route.class"
      )
      ;;
  esac

  local entrypoint
  local missing=()
  for entrypoint in "${entrypoints[@]}"; do
    if ! jar tf "$jar_path" | grep -Fxq "$entrypoint"; then
      missing+=("$entrypoint")
    fi
  done

  if [ "${#missing[@]}" -gt 0 ]; then
    echo -e "${ERR}✗ ${platform_label} bundle missing expected entrypoint class(es):${NC}"
    for entrypoint in "${missing[@]}"; do
      echo -e "  ${ERR}${entrypoint}${NC}"
    done
    return 1
  fi

  echo -e "${SUCCESS}✓ ${platform_label} bundle contains expected entrypoint classes${NC}"
  return 0
}

verify_bundles_verify_jar() {
  local jar_path="$1"
  local platform="$2"
  local platform_label
  platform_label="$(verify_bundles_platform_label "$platform")"
  local ok=true

  verify_bundles_scan_jar_for_compilation_errors "$jar_path" "${platform_label} JAR" || ok=false
  verify_bundles_check_entrypoint_classes "$jar_path" "$platform" || ok=false
  verify_bundles_check_jar_structure "$jar_path" "$platform" || ok=false

  if [ "$ok" = "true" ]; then
    return 0
  fi
  return 1
}

verify_bundles_get_gcs_metadata_sha() {
  local object_url="$1"
  local metadata_line
  metadata_line="$(curl -fsSI "$object_url" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-goog-meta-sha256"{print $2}')"
  if [ -z "$metadata_line" ]; then
    return 1
  fi
  printf '%s' "$metadata_line"
}

verify_bundles_get_s3_metadata_sha() {
  local bucket="$1"
  local key="$2"
  local region="$3"
  aws s3api head-object \
    --bucket "$bucket" \
    --key "$key" \
    --region "$region" \
    --query 'Metadata.sha256' \
    --output text 2>/dev/null
}

verify_bundles_verify_gcp_bundle() {
  local version="$1"
  local tmpdir="$2"
  local ok=true

  local object_path="gs://${GCS_BUCKET}/${GCP_ARTIFACT}"
  local object_url="https://storage.googleapis.com/${GCS_BUCKET}/${GCP_ARTIFACT}"
  local download_path="${tmpdir}/${GCP_ARTIFACT}"
  local jar_path="${tmpdir}/gcp-bundle.jar"

  echo -e "${INFO}=== GCP bundle (${GCP_ARTIFACT}) ===${NC}"

  if command -v gsutil >/dev/null 2>&1; then
    if ! gsutil -q stat "$object_path" >/dev/null 2>&1; then
      echo -e "${ERR}✗ GCP artifact not found at ${object_path}${NC}"
      return 1
    fi
    echo -e "${SUCCESS}✓ Found ${object_path}${NC}"
  else
    if ! curl -fsSI "$object_url" >/dev/null 2>&1; then
      echo -e "${ERR}✗ GCP artifact not found at ${object_url}${NC}"
      return 1
    fi
    echo -e "${SUCCESS}✓ Found ${object_url}${NC}"
  fi

  local metadata_sha=""
  if command -v gsutil >/dev/null 2>&1; then
    metadata_sha="$(gsutil stat "$object_path" 2>/dev/null | awk -F': ' '/x-goog-meta-sha256/{print $2}' | tr -d '[:space:]')"
  fi
  if [ -z "$metadata_sha" ]; then
    metadata_sha="$(verify_bundles_get_gcs_metadata_sha "$object_url" || true)"
  fi

  if [ -z "$metadata_sha" ] || [ "$metadata_sha" = "None" ]; then
    echo -e "${ERR}✗ Missing x-goog-meta-sha256 metadata on ${object_path}${NC}"
    ok=false
  else
    echo -e "${SUCCESS}✓ Object metadata sha256: ${metadata_sha}${NC}"
  fi

  echo -e "${INFO}Downloading ${object_url} ...${NC}"
  if command -v gsutil >/dev/null 2>&1; then
    gsutil -q cp "$object_path" "$download_path"
  else
    curl -fsSL "$object_url" -o "$download_path"
  fi

  local content_sha
  content_sha="$(verify_bundles_compute_sha256 "$download_path")"
  echo -e "${INFO}Downloaded content sha256: ${content_sha}${NC}"

  if [ -n "$metadata_sha" ] && [ "$metadata_sha" != "$content_sha" ]; then
    echo -e "${ERR}✗ GCP bundle sha256 mismatch (metadata vs downloaded content)${NC}"
    ok=false
  elif [ -n "$metadata_sha" ]; then
    echo -e "${SUCCESS}✓ GCP bundle sha256 matches object metadata${NC}"
  fi

  verify_bundles_jar_from_artifact "$download_path" "zip" "$jar_path" || return 1
  verify_bundles_verify_jar "$jar_path" "gcp" || ok=false

  if [ "$ok" = "true" ]; then
    return 0
  fi
  return 1
}

verify_bundles_verify_aws_bundle() {
  local version="$1"
  local tmpdir="$2"
  local ok=true
  local canonical_sha=""
  local canonical_region=""
  local download_path="${tmpdir}/${AWS_ARTIFACT}"
  local jar_path="${tmpdir}/aws-bundle.jar"

  echo -e "${INFO}=== AWS bundle (${AWS_ARTIFACT}) ===${NC}"

  local region
  for region in "${AWS_REGIONS[@]}"; do
    local bucket="${AWS_BUCKET_PREFIX}-${region}"
    local s3_uri="s3://${bucket}/${AWS_ARTIFACT}"

    if ! aws s3api head-object --bucket "$bucket" --key "$AWS_ARTIFACT" --region "$region" >/dev/null 2>&1; then
      echo -e "${ERR}✗ AWS artifact not found in ${region}: ${s3_uri}${NC}"
      ok=false
      continue
    fi

    local metadata_sha
    metadata_sha="$(verify_bundles_get_s3_metadata_sha "$bucket" "$AWS_ARTIFACT" "$region")"
    if [ -z "$metadata_sha" ] || [ "$metadata_sha" = "None" ]; then
      echo -e "${ERR}✗ Missing sha256 metadata in ${region}: ${s3_uri}${NC}"
      ok=false
      continue
    fi

    echo -e "${SUCCESS}✓ ${region}: present (metadata sha256=${metadata_sha})${NC}"

    if [ -z "$canonical_sha" ]; then
      canonical_sha="$metadata_sha"
      canonical_region="$region"
    elif [ "$metadata_sha" != "$canonical_sha" ]; then
      echo -e "${ERR}✗ sha256 metadata mismatch between ${canonical_region} (${canonical_sha}) and ${region} (${metadata_sha})${NC}"
      ok=false
    fi
  done

  if [ -z "$canonical_sha" ]; then
    return 1
  fi

  local reference_bucket="${AWS_BUCKET_PREFIX}-${canonical_region}"
  local reference_uri="s3://${reference_bucket}/${AWS_ARTIFACT}"
  echo -e "${INFO}Downloading ${reference_uri} for content verification ...${NC}"
  aws s3 cp "$reference_uri" "$download_path" --region "$canonical_region" >/dev/null

  local content_sha
  content_sha="$(verify_bundles_compute_sha256 "$download_path")"
  echo -e "${INFO}Downloaded content sha256: ${content_sha}${NC}"

  if [ "$content_sha" != "$canonical_sha" ]; then
    echo -e "${ERR}✗ AWS bundle sha256 mismatch (metadata vs downloaded content)${NC}"
    ok=false
  else
    echo -e "${SUCCESS}✓ AWS bundle sha256 matches object metadata${NC}"
  fi

  verify_bundles_jar_from_artifact "$download_path" "jar" "$jar_path" || return 1
  verify_bundles_verify_jar "$jar_path" "aws" || ok=false

  if [ "$ok" = "true" ]; then
    return 0
  fi
  return 1
}
