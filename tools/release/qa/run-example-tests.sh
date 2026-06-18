#!/bin/bash
# Run test-all.sh for a dev example and capture output.
# Usage: ./tools/release/qa/run-example-tests.sh <aws|gcp> <release>
# Example: ./tools/release/qa/run-example-tests.sh aws v0.6.6

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

if [ -z "$EXAMPLE" ] || [ -z "$RELEASE" ]; then
  printf "${ERR}Usage: %s <aws|gcp> <release>${NC}\n" "$0"
  exit 1
fi

if [ "$EXAMPLE" != "aws" ] && [ "$EXAMPLE" != "gcp" ]; then
  printf "${ERR}Example must be 'aws' or 'gcp'.${NC}\n"
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
EXAMPLE_DIR="${ROOT}/infra/examples-dev/${EXAMPLE}"
DATE_STAMP="$(date +%Y%m%d)"
OUTPUT_FILE="${EXAMPLE_DIR}/${DATE_STAMP}_${EXAMPLE}-${RELEASE}-tests.txt"

if [ ! -f "${EXAMPLE_DIR}/test-all.sh" ]; then
  printf "${ERR}test-all.sh not found in %s${NC}\n" "$EXAMPLE_DIR"
  exit 1
fi

cd "$EXAMPLE_DIR"
printf "Running ${INFO}./test-all.sh${NC} for ${INFO}%s${NC} ...\n" "$EXAMPLE"
printf "Output: ${INFO}%s${NC}\n" "$OUTPUT_FILE"

./test-all.sh 2>&1 | tee "$OUTPUT_FILE"

printf "\n${SUCCESS}Tests completed for %s.${NC}\n" "$EXAMPLE"
printf "Output: %s\n" "$OUTPUT_FILE"
