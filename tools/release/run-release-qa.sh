#!/bin/bash
# Orchestrate release QA: verify refs, apply examples, test connectors, summarize.
#
# Usage:
#   ./tools/release/run-release-qa.sh <release> [options]
#
# Options:
#   --force-bundle <true|false>   Pass to apply-example (default: true)
#   --skip-verify                 Skip release ref verification
#   --skip-apply                  Skip terraform apply (use existing deployments)
#   --skip-tests                  Skip test-all
#   --create-pr                   Run rc-to-main.sh after tests (interactive)
#   --post-pr-results             Post summaries to PR (requires --pr-number or PR from --create-pr)
#   --pr-number <N>               PR to update (for --post-pr-results without --create-pr)
#
# Examples:
#   ./tools/release/run-release-qa.sh v0.6.6
#   ./tools/release/run-release-qa.sh v0.6.6 --create-pr --post-pr-results

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=/dev/null
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; NC='\033[0m'
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
QA_DIR="${SCRIPT_DIR}/qa"

RELEASE=""
FORCE_BUNDLE="true"
SKIP_VERIFY=false
SKIP_APPLY=false
SKIP_TESTS=false
CREATE_PR=false
POST_PR_RESULTS=false
PR_NUMBER=""

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage ;;
    --force-bundle)
      FORCE_BUNDLE="${2:?--force-bundle requires true or false}"
      shift 2
      ;;
    --skip-verify) SKIP_VERIFY=true; shift ;;
    --skip-apply) SKIP_APPLY=true; shift ;;
    --skip-tests) SKIP_TESTS=true; shift ;;
    --create-pr) CREATE_PR=true; shift ;;
    --post-pr-results) POST_PR_RESULTS=true; shift ;;
    --pr-number)
      PR_NUMBER="${2:?--pr-number requires a value}"
      shift 2
      ;;
    v[0-9]*.[0-9]*.[0-9]*)
      RELEASE="$1"
      shift
      ;;
    *)
      printf "${ERR}Unknown argument: %s${NC}\n" "$1" >&2
      usage
      ;;
  esac
done

if [ -z "$RELEASE" ]; then
  printf "${ERR}Release version required (e.g. v0.6.6).${NC}\n" >&2
  usage
fi

if [[ ! "$RELEASE" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  printf "${ERR}Release must look like v0.6.6 (got: %s).${NC}\n" "$RELEASE" >&2
  exit 1
fi

RC_BRANCH="rc-${RELEASE}"
DATE_STAMP="$(date +%Y%m%d)"
AWS_TEST_LOG=""
GCP_TEST_LOG=""

cd "$ROOT"

printf "${INFO}Release QA for %s${NC}\n" "$RELEASE"
printf "Documentation: tools/release/release-qa.md\n\n"

if [ "$SKIP_VERIFY" = false ]; then
  printf "=== Step 1: Verify release refs ===\n"
  "${QA_DIR}/verify-release-refs.sh" "$RELEASE"
  printf "\n"
fi

if [ "$SKIP_APPLY" = false ]; then
  printf "=== Steps 2–3: Apply dev examples (sequential) ===\n"
  "${QA_DIR}/apply-example.sh" aws "$RELEASE" "$FORCE_BUNDLE"
  printf "\n"
  "${QA_DIR}/apply-example.sh" gcp "$RELEASE" "$FORCE_BUNDLE"
  printf "\n"
fi

if [ "$SKIP_TESTS" = false ]; then
  printf "=== Steps 4–5: Run connector tests ===\n"
  "${QA_DIR}/run-example-tests.sh" aws "$RELEASE"
  AWS_TEST_LOG="${ROOT}/infra/examples-dev/aws/${DATE_STAMP}_aws-${RELEASE}-tests.txt"
  printf "\n"
  "${QA_DIR}/run-example-tests.sh" gcp "$RELEASE"
  GCP_TEST_LOG="${ROOT}/infra/examples-dev/gcp/${DATE_STAMP}_gcp-${RELEASE}-tests.txt"
  printf "\n"
else
  AWS_TEST_LOG="$(ls -t "${ROOT}/infra/examples-dev/aws/"*"_aws-${RELEASE}-tests.txt" 2>/dev/null | head -1 || true)"
  GCP_TEST_LOG="$(ls -t "${ROOT}/infra/examples-dev/gcp/"*"_gcp-${RELEASE}-tests.txt" 2>/dev/null | head -1 || true)"
fi

printf "=== Step 6: Summarize connector results ===\n"
if [ -z "$AWS_TEST_LOG" ] || [ ! -f "$AWS_TEST_LOG" ]; then
  printf "${ERR}AWS test log not found. Run tests or pass a log via --skip-tests after a prior run.${NC}\n" >&2
  exit 1
fi
if [ -z "$GCP_TEST_LOG" ] || [ ! -f "$GCP_TEST_LOG" ]; then
  printf "${ERR}GCP test log not found.${NC}\n" >&2
  exit 1
fi

"${QA_DIR}/summarize-connector-tests.sh" aws "$AWS_TEST_LOG" "$RELEASE"
printf "\n"
"${QA_DIR}/summarize-connector-tests.sh" gcp "$GCP_TEST_LOG" "$RELEASE"
printf "\n"

AWS_CHECKLIST="${AWS_TEST_LOG}.checklist"
GCP_CHECKLIST="${GCP_TEST_LOG}.checklist"
AWS_SUMMARY="${AWS_TEST_LOG}.summary.md"
GCP_SUMMARY="${GCP_TEST_LOG}.summary.md"

if [ "$CREATE_PR" = true ]; then
  printf "=== Step 7: Create release PR ===\n"
  CURRENT_BRANCH="$(git branch --show-current)"
  if [ "$CURRENT_BRANCH" != "$RC_BRANCH" ]; then
    printf "${WARN}Checking out %s ...${NC}\n" "$RC_BRANCH"
    git checkout "$RC_BRANCH"
  fi
  PR_OUTPUT="$("${SCRIPT_DIR}/rc-to-main.sh" "$RELEASE" 2>&1 | tee /dev/stderr)"
  if [ -z "$PR_NUMBER" ]; then
    PR_NUMBER="$(echo "$PR_OUTPUT" | sed -n 's|.*/pull/\([0-9]*\).*|\1|p' | head -1)"
  fi
  printf "\n"
fi

if [ "$POST_PR_RESULTS" = true ]; then
  if [ -z "$PR_NUMBER" ]; then
    printf "${ERR}--post-pr-results requires --pr-number or a successful --create-pr.${NC}\n" >&2
    exit 1
  fi
  printf "=== Step 8: Post QA results on PR #%s ===\n" "$PR_NUMBER"
  "${QA_DIR}/update-release-pr-results.sh" \
    "$PR_NUMBER" \
    "$AWS_CHECKLIST" \
    "$GCP_CHECKLIST" \
    "$AWS_SUMMARY" \
    "$GCP_SUMMARY"
  printf "\n"
fi

printf "${SUCCESS}Release QA complete for %s.${NC}\n" "$RELEASE"
printf "\nArtifacts:\n"
printf "  AWS tests:    %s\n" "$AWS_TEST_LOG"
printf "  GCP tests:    %s\n" "$GCP_TEST_LOG"
printf "  AWS summary:  %s\n" "$AWS_SUMMARY"
printf "  GCP summary:  %s\n" "$GCP_SUMMARY"

if [ "$CREATE_PR" = false ]; then
  printf "\nNext steps:\n"
  printf "  git checkout %s\n" "$RC_BRANCH"
  printf "  ./tools/release/rc-to-main.sh %s\n" "$RELEASE"
  printf "  ./tools/release/qa/update-release-pr-results.sh <PR#> \\\n"
  printf "    %s %s %s %s\n" "$AWS_CHECKLIST" "$GCP_CHECKLIST" "$AWS_SUMMARY" "$GCP_SUMMARY"
fi

if [ "$CREATE_PR" = true ] && [ "$POST_PR_RESULTS" = false ]; then
  printf "\nPost QA to PR:\n"
  printf "  ./tools/release/qa/update-release-pr-results.sh %s \\\n" "$PR_NUMBER"
  printf "    %s %s %s %s\n" "$AWS_CHECKLIST" "$GCP_CHECKLIST" "$AWS_SUMMARY" "$GCP_SUMMARY"
fi

if [ "$CREATE_PR" = true ] && [ "$POST_PR_RESULTS" = true ]; then
  printf "\nAfter merge to main:\n"
  printf "  ./tools/release/publish.sh %s\n" "$RELEASE"
fi
