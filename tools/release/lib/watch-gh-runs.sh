#!/bin/bash

# Watch GitHub Actions release workflows briefly, then print follow-up commands.
#
# Usage:
#   ./tools/release/lib/watch-gh-runs.sh <release-tag> [workflow|run_id|url|label ...]
#
# Each tracked run is encoded as: workflow_file|run_id|url|label
#
# Behavior:
#   - Poll for up to 60 seconds while runs are in progress
#   - If any run has been active for more than 5 minutes, stop waiting and print gh run watch commands
#   - Suggest verify-bundles.sh when bundle workflows were tracked

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COLORSCHEME_SH="${SCRIPT_DIR}/../../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=/dev/null
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# shellcheck source=gh-workflow-runs.sh
source "${SCRIPT_DIR}/gh-workflow-runs.sh"

RELEASE_TAG="${1:-}"
shift || true

if [ -z "$RELEASE_TAG" ]; then
  printf "${ERR}Usage: $0 <release-tag> [workflow|run_id|url|label ...]${NC}\n"
  exit 1
fi

if [ "$#" -eq 0 ]; then
  printf "${INFO}No GitHub Actions release workflows to watch.${NC}\n"
  exit 0
fi

TRACKED=("$@")
BUNDLE_TRACKED="false"

printf "\n${INFO}=== GitHub Actions release workflows ===${NC}\n"
printf "${INFO}These jobs run in GitHub Actions (not locally). Checking status ...${NC}\n\n"

STILL_RUNNING=()
WATCH_COMMANDS=()
local_max_wait=60
elapsed=0
any_over_five_minutes="false"

refresh_status() {
  STILL_RUNNING=()
  local entry wf run_id url label status conclusion created_at age
  for entry in "${TRACKED[@]}"; do
    IFS='|' read -r wf run_id url label <<< "$entry"
    if [[ "$wf" == *bundle* ]]; then
      BUNDLE_TRACKED="true"
    fi

    local line
    line="$(gh run view "$run_id" --json status,conclusion,url,createdAt --jq '"\(.status)|\(.conclusion // "")|\(.url)|\(.createdAt // "")"' 2>/dev/null || true)"
    if [ -z "$line" ]; then
      printf "${WARN}? ${label}: could not fetch run ${run_id}${NC}\n"
      WATCH_COMMANDS+=("gh run watch ${run_id}  # ${label}")
      continue
    fi

    IFS='|' read -r status conclusion url created_at <<< "$line"
    age="$(gh_workflow_runs_age_seconds "$created_at")"

    case "$status" in
      completed)
        if [ "$conclusion" = "success" ]; then
          printf "${SUCCESS}✓${NC} ${label}: completed successfully\n"
        else
          printf "${ERR}✗${NC} ${label}: completed with status ${conclusion}\n"
        fi
        printf "    ${INFO}${url}${NC}\n"
        ;;
      *)
        printf "${INFO}…${NC} ${label}: ${status} (run ${run_id})\n"
        printf "    ${INFO}${url}${NC}\n"
        STILL_RUNNING+=("$entry")
        WATCH_COMMANDS+=("gh run watch ${run_id}  # ${label}")
        if [ "$age" -gt 300 ]; then
          any_over_five_minutes="true"
        fi
        ;;
    esac
  done
}

refresh_status

if [ "${#STILL_RUNNING[@]}" -eq 0 ]; then
  printf "\n${SUCCESS}All tracked GitHub Actions workflows have finished.${NC}\n"
else
  if [ "$any_over_five_minutes" = "true" ]; then
    printf "\n${WARN}Some workflows have been running for more than 5 minutes; not waiting further.${NC}\n"
  else
    printf "\n${INFO}Watching in-progress workflows for up to ${local_max_wait}s ...${NC}\n"
    while [ "$elapsed" -lt "$local_max_wait" ] && [ "${#STILL_RUNNING[@]}" -gt 0 ]; do
      sleep 5
      elapsed=$((elapsed + 5))
      refresh_status
    done

    if [ "${#STILL_RUNNING[@]}" -gt 0 ]; then
      printf "\n${WARN}Some workflows are still running after ${local_max_wait}s.${NC}\n"
    else
      printf "\n${SUCCESS}All tracked workflows completed during watch window.${NC}\n"
    fi
  fi

  if [ "${#WATCH_COMMANDS[@]}" -gt 0 ]; then
    printf "\n${INFO}To watch remaining runs interactively:${NC}\n"
    for cmd in "${WATCH_COMMANDS[@]}"; do
      printf "  ${CODE}${cmd}${NC}\n"
    done
  fi
fi

if [ "$BUNDLE_TRACKED" = "true" ]; then
  printf "\n${INFO}Once bundle workflows finish, verify published artifacts:${NC}\n"
  printf "  ${CODE}./tools/release/verify-bundles.sh ${RELEASE_TAG}${NC}\n"
fi

printf "\n"
