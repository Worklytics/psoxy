#!/bin/bash

# Publish release bundles (AWS and GCP) via the publish-bundles GitHub Actions workflow.
# Usage: ./publish-rc-bundles-via-gh.sh [ref]
#
# Triggers (or reuses) the combined publish-bundles.yaml workflow, which builds AWS + GCP
# bundles and runs verify-bundles in CI.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COLORSCHEME_SH="${SCRIPT_DIR}/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    # shellcheck source=/dev/null
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=lib/gh-workflow-runs.sh
source "${SCRIPT_DIR}/lib/gh-workflow-runs.sh"

BUNDLES_WORKFLOW="publish-bundles.yaml"

REF="${1:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')}"
if [ "$REF" = "HEAD" ]; then
    printf "${ERR}Error: Detached HEAD state detected. Please provide a branch, tag, or other git ref.${NC}\n"
    exit 1
fi
if [ -z "$REF" ]; then
    printf "${WARN}Warning: Could not determine current branch, using 'main'${NC}\n"
    REF="main"
fi

printf "${INFO}=== Publishing Release Bundles via GitHub Actions ===${NC}\n"
printf "${INFO}Ref:${NC} ${SUCCESS}${REF}${NC}\n"
printf "${INFO}Workflow:${NC} ${CODE}${BUNDLES_WORKFLOW}${NC} (AWS + GCP build, then verify)\n\n"

line="$(gh_workflow_runs_trigger_or_use_existing "$BUNDLES_WORKFLOW" "$REF" "Release bundles (AWS + GCP)")" || exit 1
printf '%s\n' "$line"
