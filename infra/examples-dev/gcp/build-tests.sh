#!/bin/bash
# Generate connector test scripts from Terraform outputs.
#
# Thin wrapper for an example root. `terraform init` clones the repo that
# provides the modules into `.terraform/modules/`. This runs
# `tools/build-test-scripts-from-output.sh` from that clone, so the generated
# `test-*.sh` scripts match the module version that was initialized.
#
# Usage (from an example directory):
#   ./build-tests.sh
#   ./build-tests.sh /path/to/psoxy   # explicit repo, when no .terraform clone

set -euo pipefail

if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
  ERR=$(tput setaf 1)
  WARN=$(tput setaf 3)
  INFO=$(tput setaf 4)
  NC=$(tput sgr0)
else
  ERR='\033[0;31m'
  WARN='\033[1;33m'
  INFO='\033[0;34m'
  NC='\033[0m'
fi

EXAMPLE_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$EXAMPLE_ROOT"

GENERATOR_REL="tools/build-test-scripts-from-output.sh"
EXPLICIT_REPO="${1:-}"

resolve_repo_root() {
  local candidate="$1"
  candidate="${candidate%/}"
  if [[ -d "$candidate" ]]; then
    candidate="$(cd "$candidate" && pwd -P)"
  fi
  while [[ -n "$candidate" && "$candidate" != "/" ]]; do
    if [[ -f "${candidate}/${GENERATOR_REL}" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
    local next
    next="$(dirname "$candidate")"
    if [[ "$next" == "$candidate" ]]; then
      break
    fi
    candidate="$next"
  done
  return 1
}

GENERATOR=""

if [[ -n "$EXPLICIT_REPO" ]]; then
  if REPO_ROOT="$(resolve_repo_root "$EXPLICIT_REPO")"; then
    GENERATOR="${REPO_ROOT}/${GENERATOR_REL}"
  else
    printf "${ERR}Could not find %s at or above %s.${NC}\n" "$GENERATOR_REL" "$EXPLICIT_REPO" >&2
    exit 1
  fi
else
  CLONE_GENERATOR="${EXAMPLE_ROOT}/.terraform/modules/psoxy/${GENERATOR_REL}"
  if [[ -f "$CLONE_GENERATOR" ]]; then
    GENERATOR="$CLONE_GENERATOR"
  elif [[ -d "${EXAMPLE_ROOT}/.terraform/modules" ]]; then
    while IFS= read -r candidate; do
      GENERATOR="$candidate"
      break
    done < <(find "${EXAMPLE_ROOT}/.terraform/modules" -type f -path "*/${GENERATOR_REL}" 2>/dev/null)
  fi

  if [[ -z "$GENERATOR" ]]; then
    if REPO_ROOT="$(resolve_repo_root "$EXAMPLE_ROOT")"; then
      GENERATOR="${REPO_ROOT}/${GENERATOR_REL}"
      printf "${WARN}No generator in .terraform/modules; using %s${NC}\n" "$GENERATOR" >&2
    fi
  fi
fi

if [[ -z "$GENERATOR" || ! -f "$GENERATOR" ]]; then
  printf "${ERR}Could not find %s.${NC}\n" "$GENERATOR_REL" >&2
  printf "Run ${INFO}terraform init${NC} from this directory so the module clone exists under .terraform/modules/, or pass a repo path:\n" >&2
  printf "  ${INFO}./build-tests.sh /path/to/psoxy${NC}\n" >&2
  exit 1
fi

exec "$GENERATOR" "$EXAMPLE_ROOT"
