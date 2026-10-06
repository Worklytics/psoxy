#!/bin/bash
# Confirm GCP IAM permission IDs from infra/modules/psoxy-constants are permissions
# that can be added to a custom role.
#
# The catalog comes from `gcloud iam list-testable-permissions` for a project.
# That is the set Google allows in a custom role at that project, which is what
# these constants are for.
#
# Usage:
#   ./tools/gcp/check-gcp-iam-permissions.sh
#   GCP_PROJECT_ID=my-project ./tools/gcp/check-gcp-iam-permissions.sh
#
# Tests pass fixture files and do not call gcloud or terraform:
#   ./tools/gcp/check-gcp-iam-permissions.sh \
#     --permissions-file perms.txt --catalog-file catalog.txt

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODULE="${ROOT}/infra/modules/psoxy-constants"

COLORSCHEME_SH="${ROOT}/tools/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=../set-term-colorscheme.sh
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

PERMISSIONS_FILE=""
CATALOG_FILE=""
PROJECT="${GCP_PROJECT_ID:-}"

usage() {
  printf 'Usage: %s [--permissions-file FILE] [--catalog-file FILE] [--project PROJECT_ID]\n' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --permissions-file)
      PERMISSIONS_FILE="${2:-}"
      shift 2
      ;;
    --catalog-file)
      CATALOG_FILE="${2:-}"
      shift 2
      ;;
    --project)
      PROJECT="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf "${ERR}Unknown argument: %s${NC}\n" "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

WORK="$(mktemp -d)"
cleanup() {
  if [[ "${KEEP_TMP:-}" != 1 ]]; then
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf "${ERR}%s is required.${NC}\n" "$1" >&2
    exit 1
  fi
}

# Lines are "output_name<TAB>permission".
collect_permissions_from_terraform() {
  local dest="$1"
  require_cmd terraform
  require_cmd jq

  # Run in the module directory so its local module source (../../modules/env-id)
  # resolves. State and plugin data stay outside the repo.
  local state="${WORK}/terraform.tfstate"
  export TF_DATA_DIR="${WORK}/tfdata"
  export TF_IN_AUTOMATION=1

  printf "${INFO}Reading permission outputs from psoxy-constants.${NC}\n"
  terraform -chdir="$MODULE" init -backend=false -input=false -no-color >/dev/null
  terraform -chdir="$MODULE" apply -auto-approve -input=false -no-color \
    -state="$state" -backup="${WORK}/terraform.tfstate.backup" >/dev/null
  terraform -chdir="$MODULE" output -json -no-color -state="$state" \
    | jq -r '
        to_entries[]
        | select(.key | test("gcp_perm"))
        | .key as $output
        | .value.value[]?
        | select(type == "string" and length > 0)
        | "\($output)\t\(.)"
      ' > "$dest"
}

resolve_project() {
  if [[ -n "$PROJECT" ]]; then
    printf '%s' "$PROJECT"
    return 0
  fi

  local configured account host
  configured="$(gcloud config get-value project 2>/dev/null || true)"
  if [[ -n "$configured" && "$configured" != "(unset)" ]]; then
    printf '%s' "$configured"
    return 0
  fi

  account="$(gcloud config get-value account 2>/dev/null || true)"
  if [[ "$account" == *.iam.gserviceaccount.com ]]; then
    host="${account#*@}"
    printf '%s' "${host%.iam.gserviceaccount.com}"
    return 0
  fi

  return 1
}

collect_catalog_from_gcloud() {
  local dest="$1"
  require_cmd gcloud

  if ! PROJECT="$(resolve_project)"; then
    printf "${ERR}No GCP project.${NC} Set ${CODE}GCP_PROJECT_ID${NC} or ${CODE}gcloud config set project${NC}.\n" >&2
    exit 1
  fi

  local resource="//cloudresourcemanager.googleapis.com/projects/${PROJECT}"
  printf "${INFO}Listing testable IAM permissions on %s.${NC}\n" "$resource"
  if ! gcloud iam list-testable-permissions "$resource" \
      --format='value(name)' --quiet > "$dest"; then
    printf "${ERR}gcloud iam list-testable-permissions failed.${NC}\n" >&2
    printf "The caller needs access to query testable permissions on %s.\n" "$PROJECT" >&2
    exit 1
  fi
}

if [[ -z "$PERMISSIONS_FILE" ]]; then
  PERMISSIONS_FILE="${WORK}/permissions.tsv"
  collect_permissions_from_terraform "$PERMISSIONS_FILE"
fi

if [[ -z "$CATALOG_FILE" ]]; then
  CATALOG_FILE="${WORK}/catalog.txt"
  collect_catalog_from_gcloud "$CATALOG_FILE"
fi

if [[ ! -s "$PERMISSIONS_FILE" ]]; then
  printf "${ERR}No GCP permission IDs found.${NC}\n" >&2
  exit 1
fi

if [[ ! -s "$CATALOG_FILE" ]]; then
  printf "${ERR}The testable-permission catalog is empty.${NC}\n" >&2
  exit 1
fi

# Normalize fixture lines that are a bare permission into a tab-separated row.
NORMALIZED="${WORK}/normalized.tsv"
awk -F '\t' '
  NF == 1 && $1 != "" { print "permissions\t" $1; next }
  NF >= 2 && $2 != "" { print $1 "\t" $2 }
' "$PERMISSIONS_FILE" > "$NORMALIZED"

PERM_IDS="${WORK}/permission-ids.txt"
cut -f2 "$NORMALIZED" | sort -u > "$PERM_IDS"
sort -u "$CATALOG_FILE" > "${WORK}/catalog.sorted"

MISSING="${WORK}/missing.txt"
comm -23 "$PERM_IDS" "${WORK}/catalog.sorted" > "$MISSING" || true

CHECKED="$(wc -l < "$PERM_IDS" | tr -d ' ')"
CATALOG_COUNT="$(wc -l < "${WORK}/catalog.sorted" | tr -d ' ')"

if [[ -s "$MISSING" ]]; then
  printf "${ERR}%s permission ID(s) from psoxy-constants are not testable on this project.${NC}\n" "$(wc -l < "$MISSING" | tr -d ' ')" >&2
  printf "Custom roles can include only permissions returned by ${CODE}gcloud iam list-testable-permissions${NC}.\n" >&2
  while IFS= read -r perm; do
    outputs="$(awk -F '\t' -v p="$perm" '$2 == p { print $1 }' "$NORMALIZED" | sort -u | paste -sd ', ' -)"
    printf "  ${CODE}%s${NC} (%s)\n" "$perm" "$outputs" >&2
  done < "$MISSING"
  exit 1
fi

printf "${SUCCESS}Checked %s GCP permission IDs from psoxy-constants; each is among %s testable permissions.${NC}\n" \
  "$CHECKED" "$CATALOG_COUNT"
