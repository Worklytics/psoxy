#!/bin/bash
# Install tools/psoxy-test npm deps if they are missing in this checkout.
# Safe to re-run: skips when node_modules already has the expected packages.
# Worktrees do not share node_modules; Terraform state from another worktree is not proof this checkout is installed.

set -euo pipefail

PATH_TO_TOOLS="${1:-$(pwd)/tools}"

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=set-term-colorscheme.sh
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

TEST_TOOL_ROOT="${PATH_TO_TOOLS}/psoxy-test"
CHALK_PKG="${TEST_TOOL_ROOT}/node_modules/chalk/package.json"

if [ ! -d "${TEST_TOOL_ROOT}" ]; then
  printf "${ERR}No test tool source found at ${TEST_TOOL_ROOT}. Failed to install test tool.${NC}\n"
  exit 1
fi

test_tool_installed_here() {
  [ -f "${CHALK_PKG}" ]
}

if test_tool_installed_here; then
  printf "psoxy-test already installed at ${SUCCESS}${TEST_TOOL_ROOT}${NC}\n"
  exit 0
fi

if ! command -v npm >/dev/null 2>&1; then
  printf "${ERR}NPM / Node.JS not available; could not install test tool. Install Node.js (https://nodejs.org/ LTS version preferred), then re-run.${NC}\n"
  exit 1
fi

printf "Installing ${INFO}psoxy-test${NC} into this checkout (${CODE}${TEST_TOOL_ROOT}${NC}) ...\n"
npm --no-audit --no-fund --prefix "${TEST_TOOL_ROOT}" install

if ! test_tool_installed_here; then
  printf "${ERR}npm install finished but ${CODE}chalk${NC} is still missing under ${CODE}${TEST_TOOL_ROOT}/node_modules${NC}.${NC}\n"
  exit 1
fi

printf "Test tool ${SUCCESS}successfully${NC} installed at ${SUCCESS}${TEST_TOOL_ROOT}${NC}\n"
