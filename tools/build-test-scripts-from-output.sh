#!/bin/bash
# Materialize connector test scripts from Terraform outputs.
#
# Synthesizes the same test-*.sh / test-all.sh scripts that Terraform writes via local_file,
# using standard root outputs (connector instances, aws_region, psoxy_base_dir, etc.).
#
# Works wherever Terraform state is accessible (local, CI, Terraform Cloud, etc.).
#
# Usage:
#   ./tools/build-test-scripts-from-output.sh [terraform-config-dir] [output-dir]
#
# Environment:
#   PSOXY_BASE_DIR   Override psoxy_base_dir Terraform output
#   TF_WORKSPACE     Terraform workspace to select

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=/dev/null
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

if ! command -v terraform >/dev/null 2>&1; then
  printf "${ERR}Terraform CLI not found.${NC}\n" >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  printf "${ERR}Node.js not found.${NC} Required to build test scripts.\n" >&2
  exit 1
fi

GENERATOR="$(dirname "$0")/build-test-scripts-from-output.mjs"
if [ ! -f "$GENERATOR" ]; then
  printf "${ERR}Generator not found:${NC} %s\n" "$GENERATOR" >&2
  exit 1
fi

if node "$GENERATOR" "$@"; then
  printf "${SUCCESS}Done.${NC} Run ${CODE}./test-all.sh${NC} from your output directory to test all connectors.\n"
else
  exit 1
fi
