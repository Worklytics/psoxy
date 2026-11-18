#!/bin/bash
# Shared apply for infra/examples-dev/{aws,gcp}. Invoked by each example's ./apply.

set -euo pipefail

EXAMPLE_DIR="$(pwd)"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -d "${EXAMPLE_DIR}/.azure" ]; then
  export AZURE_CONFIG_DIR="${EXAMPLE_DIR}/.azure"
fi

COLORSCHEME_SH="${REPO_ROOT}/tools/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=set-term-colorscheme.sh
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

"${REPO_ROOT}/tools/examples-dev-apply-preflight.sh"

# node_modules is worktree-local and gitignored. Shared remote Terraform state may
# already record null_resource.install_test_tool from another checkout, so apply
# would skip npm even when this worktree cannot run ./test-*.sh.
printf "\n${INFO}Test tool (this worktree)${NC}\n"
"${REPO_ROOT}/tools/install-test-tool.sh" "${REPO_ROOT}/tools"

read -r -p "Do you want to force rebuild of the bundle? (eg, have you made java code changes?) (Y/n): " force_bundle

if [ "${force_bundle:-}" = "y" ] || [ "${force_bundle:-}" = "Y" ] || [ "${force_bundle:-}" = "" ]; then
  force_bundle="true"
else
  force_bundle="false"
fi

printf "\n${INFO}terraform apply -auto-approve -var=force_bundle=%s${NC}\n\n" "$force_bundle"
terraform apply -auto-approve -var="force_bundle=$force_bundle"
