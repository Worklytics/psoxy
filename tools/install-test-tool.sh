#!/bin/bash
# Install test tool, if npm available

PATH_TO_TOOLS=${1:-$(pwd)/tools}

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

TEST_TOOL_ROOT="${PATH_TO_TOOLS}/psoxy-test"

if [ ! -d ${TEST_TOOL_ROOT} ]; then
  printf "${RED}No test tool source found at ${TEST_TOOL_ROOT}. Failed to install test tool.${NC}\n"
  exit
fi

if npm -v &> /dev/null ; then
  printf "Installing ${BLUE}psoxy-test${NC} tool ...\n"
  npm --no-audit --no-fund --prefix "${TEST_TOOL_ROOT}" install
  printf "Test tool ${GREEN}successfully${NC} installed at ${GREEN}${TEST_TOOL_ROOT}${NC}\n"
else
  printf "${RED}NPM / Node.JS not available; could not install test tool. We recommend installing Node.js ( https://nodejs.org/ LTS version preferred), then re-running this init script.${NC}\n"
fi
