#!/bin/bash

# Shared helpers for working with prebuilt deployment bundles in Terraform configs.
# Sourced by tools/upgrade-terraform-modules.sh and tools/init-tfvars.sh.

deployment_bundle_release_parts() {
  local release="$1"
  local is_rc="false"
  local version="$release"

  if [[ "$version" =~ ^rc- ]]; then
    is_rc="true"
    version="${version#rc-}"
  fi
  version="${version#v}"

  if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    return 1
  fi

  DEPLOYMENT_BUNDLE_VERSION="$version"
  DEPLOYMENT_BUNDLE_IS_RC="$is_rc"
}

deployment_bundle_artifact_filename() {
  local platform="$1"
  local version="$2"
  local is_rc="$3"
  local rc_suffix=""

  if [ "$is_rc" = "true" ]; then
    rc_suffix="-rc"
  fi

  case "$platform" in
    aws) printf 'psoxy-aws-%s%s.jar' "$version" "$rc_suffix" ;;
    gcp) printf 'psoxy-gcp-%s%s.zip' "$version" "$rc_suffix" ;;
    *) return 1 ;;
  esac
}

deployment_bundle_platform_from_path() {
  local bundle_path="$1"

  if [[ "$bundle_path" =~ ^s3://psoxy-public-artifacts-[^/]+/psoxy-aws- ]]; then
    printf 'aws'
    return 0
  fi
  if [[ "$bundle_path" =~ ^gs://psoxy-public-artifacts/psoxy-gcp- ]]; then
    printf 'gcp'
    return 0
  fi
  if [[ "$bundle_path" =~ /psoxy-aws-[^/]+\.jar$ ]]; then
    printf 'aws'
    return 0
  fi
  if [[ "$bundle_path" =~ /psoxy-gcp-[^/]+\.zip$ ]]; then
    printf 'gcp'
    return 0
  fi

  return 1
}

deployment_bundle_is_public_path() {
  local bundle_path="$1"

  if [[ "$bundle_path" =~ ^s3://psoxy-public-artifacts-[^/]+/psoxy-aws- ]]; then
    return 0
  fi
  if [[ "$bundle_path" =~ ^gs://psoxy-public-artifacts/psoxy-gcp- ]]; then
    return 0
  fi

  return 1
}

deployment_bundle_version_from_path() {
  local bundle_path="$1"

  if [[ "$bundle_path" =~ psoxy-aws-([0-9]+\.[0-9]+\.[0-9]+(-rc)?)\.jar ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi
  if [[ "$bundle_path" =~ psoxy-gcp-([0-9]+\.[0-9]+\.[0-9]+(-rc)?)\.zip ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi

  return 1
}

deployment_bundle_find_tfvars_files() {
  local tfvars_file

  while IFS= read -r tfvars_file; do
    if grep -qE '^[[:space:]]*deployment_bundle[[:space:]]*=' "$tfvars_file" 2>/dev/null; then
      printf '%s\n' "$tfvars_file"
    fi
  done < <(find . -maxdepth 1 -type f \( -name '*.tfvars' -o -name '*.auto.tfvars' \) | sort)
}

deployment_bundle_get_value() {
  local tfvars_file="$1"
  grep -E '^[[:space:]]*deployment_bundle[[:space:]]*=' "$tfvars_file" 2>/dev/null \
    | head -1 \
    | sed -E 's/.*=[[:space:]]*"([^"]*)".*/\1/'
}

deployment_bundle_set_value() {
  local tfvars_file="$1"
  local bundle_path="$2"

  sed -i.bck "s|^\([[:space:]]*deployment_bundle[[:space:]]*=\).*|\1 \"${bundle_path}\"|" "$tfvars_file"
  rm -f "${tfvars_file}.bck" 2>/dev/null
}

deployment_bundle_get_aws_region() {
  local current_bundle="${1:-}"
  local tfvars_file="${2:-terraform.tfvars}"
  local aws_region=""

  if [[ "$current_bundle" =~ ^s3://psoxy-public-artifacts-([^/]+)/ ]]; then
    aws_region="${BASH_REMATCH[1]}"
  fi

  if [ -z "$aws_region" ] && [ -f "$tfvars_file" ]; then
    if grep -qE '^[[:space:]]*aws_region[[:space:]]*=' "$tfvars_file" 2>/dev/null; then
      aws_region="$(grep -E '^[[:space:]]*aws_region[[:space:]]*=' "$tfvars_file" \
        | sed -E 's/.*=[[:space:]]*"([^"]*)".*/\1/' \
        | head -1)"
    fi
  fi

  if [ -z "$aws_region" ] && command -v aws >/dev/null 2>&1; then
    aws_region="$(aws configure get region 2>/dev/null || true)"
  fi

  if [ -z "$aws_region" ]; then
    aws_region="us-east-1"
  fi

  printf '%s' "$aws_region"
}

deployment_bundle_public_path() {
  local platform="$1"
  local version="$2"
  local is_rc="$3"
  local aws_region="${4:-us-east-1}"
  local artifact_name

  artifact_name="$(deployment_bundle_artifact_filename "$platform" "$version" "$is_rc")" || return 1

  case "$platform" in
    aws) printf 's3://psoxy-public-artifacts-%s/%s' "$aws_region" "$artifact_name" ;;
    gcp) printf 'gs://psoxy-public-artifacts/%s' "$artifact_name" ;;
    *) return 1 ;;
  esac
}

deployment_bundle_public_exists() {
  local bundle_path="$1"

  case "$bundle_path" in
    s3://*)
      if ! command -v aws >/dev/null 2>&1; then
        return 1
      fi
      aws s3 ls "$bundle_path" >/dev/null 2>&1
      ;;
    gs://*)
      if command -v gsutil >/dev/null 2>&1; then
        gsutil -q stat "$bundle_path" >/dev/null 2>&1
        return $?
      fi
      if command -v curl >/dev/null 2>&1; then
        local http_url="${bundle_path#gs://}"
        curl -fsSI "https://storage.googleapis.com/${http_url}" >/dev/null 2>&1
        return $?
      fi
      return 1
      ;;
    *)
      return 1
      ;;
  esac
}

deployment_bundle_detect_host_platform() {
  if grep -q 'provider "aws"' *.tf 2>/dev/null; then
    printf 'aws'
  else
    printf 'gcp'
  fi
}

deployment_bundle_target_artifact_label() {
  local version="$1"
  local is_rc="$2"

  if [ "$is_rc" = "true" ]; then
    printf '%s-rc' "$version"
  else
    printf '%s' "$version"
  fi
}

deployment_bundle_maybe_upgrade() {
  local next_release="$1"
  local tfvars_files=()
  local tfvars_file
  local any_bundle=false
  local bundle_updated=false
  local any_version_mismatch=false

  if ! deployment_bundle_release_parts "$next_release"; then
    printf "${WARN}Warning:${NC} Could not parse target release ${INFO}${next_release}${NC} for deployment bundle update.\n"
    return 0
  fi

  local target_version="$DEPLOYMENT_BUNDLE_VERSION"
  local target_is_rc="$DEPLOYMENT_BUNDLE_IS_RC"
  local target_label
  target_label="$(deployment_bundle_target_artifact_label "$target_version" "$target_is_rc")"

  while IFS= read -r tfvars_file; do
    tfvars_files+=("$tfvars_file")
  done < <(deployment_bundle_find_tfvars_files)

  if [ "${#tfvars_files[@]}" -eq 0 ]; then
    return 0
  fi

  for tfvars_file in "${tfvars_files[@]}"; do
    local current_bundle
    current_bundle="$(deployment_bundle_get_value "$tfvars_file")"
    if [ -z "$current_bundle" ]; then
      continue
    fi

    any_bundle=true
    local current_version=""
    current_version="$(deployment_bundle_version_from_path "$current_bundle" 2>/dev/null || true)"

    printf "\nFound ${INFO}deployment_bundle${NC} in ${CODE}${tfvars_file}${NC}:\n"
    printf "  ${INFO}${current_bundle}${NC}\n"
    if [ -n "$current_version" ]; then
      printf "  (bundle version: ${CODE}${current_version}${NC}; target module version: ${SUCCESS}${target_label}${NC})\n"
    fi
    printf "\n"

    if deployment_bundle_is_public_path "$current_bundle"; then
      local platform
      platform="$(deployment_bundle_platform_from_path "$current_bundle")"
      local aws_region="us-east-1"
      if [ "$platform" = "aws" ]; then
        aws_region="$(deployment_bundle_get_aws_region "$current_bundle" "$tfvars_file")"
      fi

      local new_bundle_path
      new_bundle_path="$(deployment_bundle_public_path "$platform" "$target_version" "$target_is_rc" "$aws_region")"

      if [ "$current_bundle" = "$new_bundle_path" ]; then
        printf "${SUCCESS}✓ deployment_bundle already references the published bundle for ${next_release}.${NC}\n\n"
        continue
      fi

      if ! deployment_bundle_public_exists "$new_bundle_path"; then
        printf "${WARN}Warning:${NC} Published bundle for ${SUCCESS}${next_release}${NC} was not found at:\n"
        printf "  ${INFO}${new_bundle_path}${NC}\n"
        printf "Bundle reference in ${CODE}${tfvars_file}${NC} was not updated. Build locally or wait for publish.\n\n"
        continue
      fi

      printf "Published bundle is available at:\n"
      printf "  ${SUCCESS}${new_bundle_path}${NC}\n\n"
      read -p "Update deployment_bundle in ${tfvars_file} to match ${next_release}? [Y/n] " response
      if [[ -z "$response" || "$response" =~ ^[Yy]$ ]]; then
        deployment_bundle_set_value "$tfvars_file" "$new_bundle_path"
        printf "Updated ${INFO}deployment_bundle${NC} in ${CODE}${tfvars_file}${NC} to:\n"
        printf "  ${SUCCESS}${new_bundle_path}${NC}\n\n"
        bundle_updated=true
      else
        printf "Skipped updating ${CODE}${tfvars_file}${NC}.\n\n"
      fi
      continue
    fi

    printf "This appears to be a custom bundle (not in Worklytics public artifact buckets).\n"
    read -p "Run './update-bundle' to rebuild it for ${next_release}? [Y/n] " response
    if [[ -z "$response" || "$response" =~ ^[Yy]$ ]]; then
      if [ -f "./update-bundle" ]; then
        ./update-bundle
        bundle_updated=true
      else
        printf "${ERR}Error:${NC} ./update-bundle script not found. Build the bundle manually.\n\n"
      fi
    else
      printf "Bundle not updated.\n\n"
    fi

    if [ -n "$current_version" ] && [ "$current_version" != "$target_label" ]; then
      any_version_mismatch=true
    fi
  done

  if [ "$any_bundle" = "true" ]; then
    if [ "$bundle_updated" = "true" ]; then
      printf "${INFO}Note:${NC} Ensure the deployment bundle version matches Terraform modules ${SUCCESS}${next_release}${NC} before applying.\n\n"
    elif [ "$any_version_mismatch" = "true" ]; then
      printf "${WARN}Warning:${NC} deployment_bundle version may not match Terraform modules (${target_label}).\n"
      printf "Mismatch can cause runtime errors until you update the bundle reference or rebuild locally.\n\n"
    fi
  fi
}
