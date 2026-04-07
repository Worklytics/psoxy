#!/bin/bash
# Install test tool, if npm available

PATH_TO_TOOLS=${1:-$(pwd)/tools}

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

TEST_TOOL_ROOT="${PATH_TO_TOOLS}/psoxy-test"

if [ ! -d ${TEST_TOOL_ROOT} ]; then
  printf "${ERR}No test tool source found at ${TEST_TOOL_ROOT}. Failed to install test tool.${NC}\n"
  exit
fi

if npm -v &> /dev/null ; then
  printf "Installing ${INFO}psoxy-test${NC} tool ...\n"
  npm --no-audit --no-fund --prefix "${TEST_TOOL_ROOT}" install
  printf "Test tool ${SUCCESS}successfully${NC} installed at ${SUCCESS}${TEST_TOOL_ROOT}${NC}\n"
else
  printf "${ERR}NPM / Node.JS not available; could not install test tool. We recommend installing Node.js ( https://nodejs.org/ LTS version preferred), then re-running this init script.${NC}\n"
fi
