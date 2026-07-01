#!/bin/bash
# Non-interactive terraform apply for a dev example, saving plan + apply logs.
# Usage: ./tools/release/qa/apply-example.sh <aws|gcp> <release> [force_bundle]
# Example: ./tools/release/qa/apply-example.sh aws v0.6.6 true

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/../../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=/dev/null
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; NC='\033[0m'
fi

EXAMPLE="${1:-}"
RELEASE="${2:-}"
FORCE_BUNDLE="${3:-true}"

if [ -z "$EXAMPLE" ] || [ -z "$RELEASE" ]; then
  printf "${ERR}Usage: %s <aws|gcp> <release> [force_bundle]${NC}\n" "$0"
  exit 1
fi

if [ "$EXAMPLE" != "aws" ] && [ "$EXAMPLE" != "gcp" ]; then
  printf "${ERR}Example must be 'aws' or 'gcp'.${NC}\n"
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
EXAMPLE_DIR="${ROOT}/infra/examples-dev/${EXAMPLE}"
DATE_STAMP="$(date +%Y%m%d)"
PLAN_LOG="${EXAMPLE_DIR}/${DATE_STAMP}_${EXAMPLE}-${RELEASE}-plan.txt"
APPLY_LOG="${EXAMPLE_DIR}/${DATE_STAMP}_${EXAMPLE}-${RELEASE}-apply.txt"

if [ ! -d "$EXAMPLE_DIR" ]; then
  printf "${ERR}Example directory not found: %s${NC}\n" "$EXAMPLE_DIR"
  exit 1
fi

cd "$EXAMPLE_DIR"

printf "Running ${INFO}terraform plan${NC} for ${INFO}%s${NC} (release %s) ...\n" "$EXAMPLE" "$RELEASE"
printf "Plan log: ${INFO}%s${NC}\n" "$PLAN_LOG"

terraform plan -var="force_bundle=${FORCE_BUNDLE}" -no-color 2>&1 | tee "$PLAN_LOG"

printf "\n${WARN}Review the plan log above before continuing.${NC}\n"
printf "Applying ${INFO}%s${NC} with force_bundle=%s ...\n" "$EXAMPLE" "$FORCE_BUNDLE"
printf "Apply log: ${INFO}%s${NC}\n" "$APPLY_LOG"

terraform apply -auto-approve -var="force_bundle=${FORCE_BUNDLE}" -no-color 2>&1 | tee "$APPLY_LOG"

printf "\n${SUCCESS}Apply completed for %s.${NC}\n" "$EXAMPLE"
printf "Logs:\n  plan:  %s\n  apply: %s\n" "$PLAN_LOG" "$APPLY_LOG"
