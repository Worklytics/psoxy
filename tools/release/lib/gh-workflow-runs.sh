#!/bin/bash

# Shared helpers for triggering and monitoring GitHub Actions workflows during release.
# Sourced by publish.sh, publish-rc-bundles-via-gh.sh, and watch-gh-runs.sh.

gh_workflow_runs_require_gh() {
  if ! command -v gh >/dev/null 2>&1; then
    printf "${ERR}Error: GitHub CLI (gh) is required.${NC}\n"
    return 1
  fi
  if ! gh auth status >/dev/null 2>&1; then
    printf "${ERR}Error: Not authenticated with GitHub CLI. Run 'gh auth login'.${NC}\n"
    return 1
  fi
  return 0
}

# Find the most recent workflow run for a workflow file on a given git ref.
# Prints: run_id|status|conclusion|url|created_at (ISO8601)
gh_workflow_runs_find_latest() {
  local workflow_file="$1"
  local ref="$2"

  gh run list \
    --workflow="$workflow_file" \
    --limit 15 \
    --json databaseId,status,conclusion,url,createdAt,headBranch \
    --jq '[.[] | select(.headBranch == "'"$ref"'")][0] | if . == null then "" else "\(.databaseId)|\(.status)|\(.conclusion // "")|\(.url)|\(.createdAt // "")" end' \
    2>/dev/null || true
}

# Print human-readable status for the latest workflow run on a ref (stderr).
# Tracks the run in RELEASE_GH_RUNS when found. Returns 0 always (informational).
gh_workflow_runs_report_status() {
  local workflow_file="$1"
  local ref="$2"
  local label="$3"
  local line run_id status conclusion url

  if ! gh_workflow_runs_require_gh 2>/dev/null; then
    printf "${WARN}${label}:${NC} skipped GitHub Actions status check (gh not available)\n" >&2
    return 0
  fi

  line="$(gh_workflow_runs_find_latest "$workflow_file" "$ref")"
  if [ -z "$line" ] || [ -z "${line%%|*}" ] || [ "${line%%|*}" = "null" ]; then
    printf "${INFO}${label}:${NC} no GitHub Actions run found yet for ${ref} (${CODE}${workflow_file}${NC})\n" >&2
    printf "  ${INFO}Tag push may still be registering; check with: gh run list --workflow=${workflow_file}${NC}\n" >&2
    return 0
  fi

  IFS='|' read -r run_id status conclusion url _ <<< "$line"

  if [ "$status" = "completed" ] && [ -n "$conclusion" ]; then
    if [ "$conclusion" = "success" ]; then
      printf "${SUCCESS}✓${NC} ${label}: GitHub Actions run ${SUCCESS}${run_id}${NC} completed successfully\n" >&2
    else
      printf "${ERR}✗${NC} ${label}: GitHub Actions run ${run_id} completed with ${conclusion}\n" >&2
    fi
  else
    printf "${INFO}…${NC} ${label}: GitHub Actions run ${SUCCESS}${run_id}${NC} is ${status}\n" >&2
  fi
  printf "  ${INFO}${url}${NC}\n" >&2
  gh_workflow_runs_track "$workflow_file" "$run_id" "$url" "$label"
  return 0
}

# Wait briefly for a workflow run to appear after a trigger (tag push or workflow_dispatch).
gh_workflow_runs_wait_for_run() {
  local workflow_file="$1"
  local ref="$2"
  local max_attempts="${3:-15}"
  local attempt=0
  local line=""

  while [ "$attempt" -lt "$max_attempts" ]; do
    line="$(gh_workflow_runs_find_latest "$workflow_file" "$ref")"
    if [ -n "$line" ] && [ "${line%%|*}" != "" ] && [ "${line%%|*}" != "null" ]; then
      printf '%s' "$line"
      return 0
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  return 1
}

# Trigger workflow_dispatch if no recent in-progress/queued run exists for this ref.
# Prints: workflow_file|run_id|url|triggered (true|false)
gh_workflow_runs_trigger_or_use_existing() {
  local workflow_file="$1"
  local ref="$2"
  local label="$3"

  gh_workflow_runs_require_gh || return 1

  local line
  line="$(gh_workflow_runs_find_latest "$workflow_file" "$ref")"
  local run_id="${line%%|*}"
  local status=""
  local url=""
  if [ -n "$line" ]; then
    IFS='|' read -r run_id status _ url _ <<< "$line"
  fi

  if [ -n "$run_id" ] && { [ "$status" = "in_progress" ] || [ "$status" = "queued" ] || [ "$status" = "waiting" ] || [ "$status" = "pending" ]; }; then
    printf "${INFO}${label}${NC} already running via GitHub Actions (run ${SUCCESS}${run_id}${NC}).\n" >&2
    printf "  ${INFO}${url}${NC}\n" >&2
    printf '%s|%s|%s|false\n' "$workflow_file" "$run_id" "$url"
    return 0
  fi

  printf "${INFO}Triggering ${label} via GitHub Actions (${CODE}${workflow_file}${NC}) on ref ${SUCCESS}${ref}${NC} ...\n" >&2
  if ! gh workflow run "$workflow_file" --ref "$ref"; then
    printf "${ERR}Failed to trigger ${workflow_file} on ${ref}.${NC}\n" >&2
    return 1
  fi

  line="$(gh_workflow_runs_wait_for_run "$workflow_file" "$ref")"
  if [ -z "$line" ]; then
    printf "${WARN}Triggered ${workflow_file}, but could not find the new run yet.${NC}\n" >&2
    printf "${WARN}Check manually: ${INFO}gh run list --workflow=${workflow_file} --branch=${ref}${NC}\n" >&2
    return 1
  fi

  IFS='|' read -r run_id status _ url _ <<< "$line"
  printf "${SUCCESS}✓${NC} ${label} workflow started (run ${SUCCESS}${run_id}${NC}, status: ${status}).\n" >&2
  printf "  ${INFO}${url}${NC}\n" >&2
  printf '%s|%s|%s|true\n' "$workflow_file" "$run_id" "$url"
  return 0
}

# Register a workflow run for later monitoring. Appends to RELEASE_GH_RUNS array.
gh_workflow_runs_track() {
  local workflow_file="$1"
  local run_id="$2"
  local url="$3"
  local label="$4"
  local existing=""

  if [ -z "$run_id" ] || [ "$run_id" = "null" ]; then
    return 1
  fi

  for existing in "${RELEASE_GH_RUNS[@]}"; do
    if [[ "$existing" == "${workflow_file}|${run_id}|"* ]]; then
      return 0
    fi
  done

  RELEASE_GH_RUNS+=("${workflow_file}|${run_id}|${url}|${label}")
  return 0
}

# Parse gh_workflow_runs_trigger_or_use_existing output and track the run.
gh_workflow_runs_trigger_and_track() {
  local workflow_file="$1"
  local ref="$2"
  local label="$3"
  local line=""

  line="$(gh_workflow_runs_trigger_or_use_existing "$workflow_file" "$ref" "$label")" || return 1
  local wf run_id url triggered
  IFS='|' read -r wf run_id url triggered <<< "$line"
  gh_workflow_runs_track "$wf" "$run_id" "$url" "$label"
}

# Seconds since an ISO8601 timestamp (best effort; macOS + Linux).
gh_workflow_runs_age_seconds() {
  local created_at="$1"
  if [ -z "$created_at" ]; then
    printf '0'
    return 0
  fi
  local created_epoch now_epoch
  if created_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$created_at" "+%s" 2>/dev/null); then
    :
  elif created_epoch=$(date -d "$created_at" "+%s" 2>/dev/null); then
    :
  else
    printf '0'
    return 0
  fi
  now_epoch=$(date "+%s")
  printf '%s' "$((now_epoch - created_epoch))"
}
