#!/bin/bash
# Verify release refs were updated from an rc-v* string to vX.Y.Z before release QA.
# Usage: ./tools/release/qa/verify-release-refs.sh <release> [rc-branch]
# Example: ./tools/release/qa/verify-release-refs.sh v0.6.6
# The rc branch name may differ from the release tag (e.g. rc-v0.4.16 while
# releasing v0.5.0). Pass it as the second argument, or run from that branch.

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/../../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=/dev/null
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; NC='\033[0m'
fi

RC_BRANCH_SH="$(dirname "$0")/../lib/rc-branch.sh"
# shellcheck source=../lib/rc-branch.sh
source "$RC_BRANCH_SH"

RELEASE="${1:-}"
if [ -z "$RELEASE" ]; then
  printf "${ERR}Usage: %s <release> [rc-branch]${NC}\n" "$0"
  printf "Example: %s v0.6.6\n" "$0"
  exit 1
fi

if [[ ! "$RELEASE" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  printf "${ERR}Release must look like v0.6.6 (got: %s)${NC}\n" "$RELEASE"
  exit 1
fi

RC_RELEASE="$(resolve_rc_branch "$RELEASE" "${2:-}")"
RELEASE_NUMBER="${RELEASE#v}"
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

FAIL=0

printf "Verifying release refs for ${INFO}%s${NC} (from ${INFO}%s${NC}) ...\n" "$RELEASE" "$RC_RELEASE"
warn_if_rc_release_mismatch "$RC_RELEASE" "$RELEASE"

CURRENT_BRANCH="$(git branch --show-current)"
if [ "$CURRENT_BRANCH" != "$RC_RELEASE" ] && [ "$CURRENT_BRANCH" != "${RELEASE#v}" ]; then
  printf "${WARN}Warning: current branch is '%s'; expected '%s' or a release prep branch.${NC}\n" \
    "$CURRENT_BRANCH" "$RC_RELEASE"
fi

POM_REVISION="$(sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' java/pom.xml | head -1)"
if [ "$POM_REVISION" != "$RELEASE_NUMBER" ]; then
  printf "${ERR}java/pom.xml revision is '%s'; expected '%s'.${NC}\n" "$POM_REVISION" "$RELEASE_NUMBER"
  FAIL=1
else
  printf "${SUCCESS}java/pom.xml revision matches %s.${NC}\n" "$RELEASE_NUMBER"
fi

STALE_RC_REFS="$(git grep -n 'ref=rc-v' -- 'infra/' 'java/' 'tools/' 2>/dev/null || true)"
if [ -n "$STALE_RC_REFS" ]; then
  printf "${ERR}Found stale module refs to an rc-v* tag:${NC}\n%s\n" "$STALE_RC_REFS"
  FAIL=1
else
  printf "${SUCCESS}No stale ref=rc-v* module references under infra/, java/, or tools/.${NC}\n"
fi

MISSING_RELEASE_REFS="$( (git grep -n "ref=${RELEASE}" -- 'infra/examples-dev/' 2>/dev/null || true) | wc -l | tr -d ' ' )"
if [ "$MISSING_RELEASE_REFS" -eq 0 ]; then
  printf "${WARN}No commented ref=%s lines found in infra/examples-dev/ (expected in example .tf files).${NC}\n" "$RELEASE"
else
  printf "${SUCCESS}Found %s commented ref=%s references in examples-dev.${NC}\n" "$MISSING_RELEASE_REFS" "$RELEASE"
fi

STALE_RC_STRINGS="$(git grep -n "${RC_RELEASE}" -- 'infra/' 'java/' 'tools/' 2>/dev/null \
  | grep -v 'verify-release-refs.sh' \
  | grep -v 'prep.sh' \
  | grep -v 'rc-branch.sh' \
  | grep -v 'rc-to-main.sh' \
  | grep -v 'releases.md' \
  | grep -v 'upgrade-terraform-modules.sh' \
  | grep -v 'HealthCheckResultTest.java' \
  || true)"
if [ -n "$STALE_RC_STRINGS" ]; then
  printf "${WARN}Other %s string references remain (review; may be OK in docs/tests):${NC}\n%s\n" \
    "$RC_RELEASE" "$STALE_RC_STRINGS"
fi

if [ "$FAIL" -ne 0 ]; then
  printf "\n${ERR}Release ref verification failed.${NC}\n"
  printf "Run from repo root: ${INFO}./tools/release/prep.sh %s %s${NC}\n" "$RC_RELEASE" "$RELEASE"
  exit 1
fi

printf "\n${SUCCESS}Release ref verification passed for %s.${NC}\n" "$RELEASE"
