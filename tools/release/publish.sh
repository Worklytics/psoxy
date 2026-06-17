#!/bin/bash

# Arguments: repository name and tag name
RELEASE="$1"
PATH_TO_REPO="${2:-.}"

# Normalize PATH_TO_REPO - ensure it ends with a slash for path concatenation
if [ -n "$PATH_TO_REPO" ] && [ "${PATH_TO_REPO: -1}" != "/" ]; then
  PATH_TO_REPO="${PATH_TO_REPO}/"
fi

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

if [ ! -f "${PATH_TO_REPO}java/pom.xml" ]; then
  printf "${ERR}${PATH_TO_REPO}java/pom.xml not found. set <path-to-repo> argument to point to the root of a psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

CURRENT_RELEASE_NUMBER=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "${PATH_TO_REPO}java/pom.xml" )

if [ -z "$RELEASE" ]; then
  RELEASE="v${CURRENT_RELEASE_NUMBER}"
else
  # maintain compatibility with current interface
  if [ "$RELEASE" != "v${CURRENT_RELEASE_NUMBER}" ]; then
    printf "${ERR}Release version provided, ${RELEASE}, does not match current release version in pom.xml, ${CURRENT_RELEASE_NUMBER}. Exiting.${NC}\n"
    exit 1
  fi
fi

# die on error
set -e

git fetch origin

if git rev-parse "$RELEASE" >/dev/null 2>&1; then
  printf "${WARN}Tag ${RELEASE} already exists.${NC}\n"
  printf "Continue anyway?\n"
  read -p "(y/N) " -n 1 -r
  REPLY=${REPLY:-N}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
      printf "Continuing with existing tag ${SUCCESS}$RELEASE${NC} ...\n"
      ;;
    *)
      printf "Exiting.\n"
      exit 0
      ;;
  esac
else
  git checkout main

  git pull origin main

  # verify on main branch and clean status
  CURRENT_BRANCH=$(git branch --show-current)
  if [ "$CURRENT_BRANCH" != "main" ]; then
    printf "${ERR}Current branch is not main. Please checkout main branch and try again.${NC}\n"
    exit 1
  fi

  # verify a recent release merge from an rc- branch exists
  # rc-to-main.sh sets merge subject to "release $RELEASE from PR #..." (no "rc-" in message);
  # also accept default GitHub merge messages that include the rc- branch name
  if [ -z "$(git log --since="48 hours ago" --merges --oneline -i --fixed-strings --grep="release ${RELEASE}" --grep="rc-")" ]; then
    printf "${WARN}No recent merge from an 'rc-' branch into main found within the last 48 hours.${NC}\n"
    printf "Please ensure the release candidate (rc-) PR has been merged before publishing.\n"
    printf "Continue anyway?\n"
    read -p "(y/N) " -n 1 -r
    REPLY=${REPLY:-N}
    echo    # Move to a new line
    case "$REPLY" in
      [yY][eE][sS]|[yY])
        printf "Proceeding despite missing recent rc- merge...\n"
        ;;
      *)
        printf "Exiting.\n"
        exit 1
        ;;
    esac
  fi

  printf "Tagging ${SUCCESS}$RELEASE${NC} ...\n"
  git tag $RELEASE
fi

printf "Pushing tag ${SUCCESS}$RELEASE${NC} to origin ...\n"
git push origin $RELEASE

GH_RUNS_LIB="$(dirname "$0")/lib/gh-workflow-runs.sh"
# shellcheck source=lib/gh-workflow-runs.sh
source "$GH_RUNS_LIB"
RELEASE_GH_RUNS=()

MVN_WORKFLOW="publish-release-artifacts.yaml"
BUNDLES_WORKFLOW="publish-bundles.yaml"

printf "\n${INFO}Tag push also triggers GitHub Actions for Maven packages and release bundles.${NC}\n"
printf "${INFO}Checking workflow status via gh ...${NC}\n"
sleep 3
gh_workflow_runs_report_status "$MVN_WORKFLOW" "$RELEASE" "Maven packages (GitHub Packages)"
gh_workflow_runs_report_status "$BUNDLES_WORKFLOW" "$RELEASE" "Release bundles (AWS + GCP)"
printf "\n"

if gh release view $RELEASE >/dev/null 2>&1
then
  printf "Release ${SUCCESS}$RELEASE${NC} already exists.\n"
else
  printf "Creating release ${SUCCESS}$RELEASE${NC} in GitHub ...\n"
  gh release create --draft --generate-notes $RELEASE
fi

printf "Updating release notes for ${SUCCESS}$RELEASE${NC} ...\n"
FORMAT_NOTES_SH="$(dirname "$0")/lib/format-release-notes.sh"
modified_notes=$(gh release view $RELEASE --json body -q '.body' | "$FORMAT_NOTES_SH")

# Update release notes
gh release edit $RELEASE -n "$modified_notes"

# check if rc branch exists, and offer to delete if so
rc_branch="rc-$RELEASE"
if git rev-parse "$rc_branch" >/dev/null 2>&1; then
  printf "Delete the ${INFO}rc-${RELEASE}${NC} branch?\n"
  read -p "(Y/n) " -n 1 -r
  REPLY=${REPLY:-Y}
  echo    # Move to a new line
  case "$REPLY" in
    [yY][eE][sS]|[yY])
       git branch -d "rc-$RELEASE"
      ;;
    *)
      printf "Skipped deletion of ${INFO}rc-$RELEASE${NC}\n"
      ;;
  esac
fi

printf "Opening release ${INFO}${RELEASE}${NC} in browser; review / update notes and then publish as latest ...\n"
gh release view $RELEASE --web

# Maven artifacts — report GH status again in case it changed, then offer local publish
printf "Publish Maven artifacts to GitHub Packages locally?\n"
gh_workflow_runs_report_status "$MVN_WORKFLOW" "$RELEASE" "Maven packages (GitHub Packages)"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    LOG_FILE="/tmp/release_${RELEASE}_mvn-artifacts.log"
    set +e
    ./tools/release/publish-mvn-artifacts.sh "${PATH_TO_REPO}" &> "${LOG_FILE}"
    EXIT_CODE=$?
    set -e
    if [ $EXIT_CODE -ne 0 ]; then
      printf "${ERR}Failed to publish Maven artifacts locally.${NC}\n"
      printf "Review logs: ${INFO}cat ${LOG_FILE}${NC}\n"
      printf "Or use GitHub Actions: ${INFO}gh workflow run ${MVN_WORKFLOW} --ref ${RELEASE}${NC}\n"
      exit $EXIT_CODE
    else
      printf "${SUCCESS}✓${NC} Maven artifacts published locally\n"
      printf "See logs: ${INFO}cat ${LOG_FILE}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped local Maven publish\n"
    printf "GitHub Actions equivalent: ${INFO}gh workflow run ${MVN_WORKFLOW} --ref ${RELEASE}${NC}\n"
    ;;
esac

# Bundles — report GH status, then offer local AWS + GCP publish
printf "Publish release bundles locally (AWS + GCP)?\n"
gh_workflow_runs_report_status "$BUNDLES_WORKFLOW" "$RELEASE" "Release bundles (AWS + GCP)"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    BUNDLE_LOG="/tmp/release_${RELEASE}_bundles.log"
    : > "${BUNDLE_LOG}"
    set +e
    (
      cd "${PATH_TO_REPO}"
      ./tools/release/lib/publish-aws-bundle.sh
      AWS_EXIT=$?
      ./tools/release/lib/publish-gcp-bundle.sh
      GCP_EXIT=$?
      exit $((AWS_EXIT || GCP_EXIT))
    ) >> "${BUNDLE_LOG}" 2>&1
    EXIT_CODE=$?
    set -e
    if [ $EXIT_CODE -ne 0 ]; then
      printf "${ERR}Failed to publish bundles locally.${NC}\n"
      printf "Review logs: ${INFO}cat ${BUNDLE_LOG}${NC}\n"
      printf "Or use GitHub Actions: ${INFO}gh workflow run ${BUNDLES_WORKFLOW} --ref ${RELEASE}${NC}\n"
      exit $EXIT_CODE
    else
      printf "${SUCCESS}✓${NC} AWS and GCP bundles published locally\n"
      printf "See logs: ${INFO}cat ${BUNDLE_LOG}${NC}\n"
    fi
  ;;
  *)
    printf "Skipped local bundle publish\n"
    printf "GitHub Actions equivalent: ${INFO}gh workflow run ${BUNDLES_WORKFLOW} --ref ${RELEASE}${NC}\n"
    printf "Or trigger individually: ${INFO}gh workflow run publish-aws-bundle.yaml --ref ${RELEASE}${NC}\n"
    printf "                       ${INFO}gh workflow run publish-gcp-bundle.yaml --ref ${RELEASE}${NC}\n"
    ;;
esac

# publish docs
printf "Publish docs?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    set +e  # Temporarily disable exit on error to check exit code
    ./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO}
    EXIT_CODE=$?
    set -e  # Re-enable exit on error
    if [ $EXIT_CODE -ne 0 ]; then
      printf "${ERR}Failed to publish docs.${NC}\n"
    else
      printf "${SUCCESS}✓${NC} Docs published\n"
    fi
  ;;
  *)
    printf "Skipped publishing docs\n"
    printf "To do so manually, run:\n"
    printf "    ${INFO}./tools/release/publish-docs.sh ${RELEASE} ${PATH_TO_REPO}${NC}\n"
    ;;
esac

# prep next rc ?
printf "Prep next rc?\n"
read -p "(Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo    # Move to a new line
case "$REPLY" in
  [yY][eE][sS]|[yY])
    printf "Next release version number (e.g. ${INFO}0.6.5${NC}): "
    read -r NEXT_VERSION_NUMBER
    NEXT_VERSION_NUMBER="${NEXT_VERSION_NUMBER#v}"
    if [[ ! "$NEXT_VERSION_NUMBER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      printf "${ERR}Invalid version '${NEXT_VERSION_NUMBER}'. Expected format X.Y.Z (e.g. 0.6.5). Skipping rc prep.${NC}\n"
    else
      NEXT_RC="rc-v${NEXT_VERSION_NUMBER}"
      ./tools/release/prep.sh ${RELEASE} "${NEXT_RC}"
    fi
  ;;
  *)
    printf "Skipped prepping next rc\n"
    printf "To do so manually, run:\n"
    printf "    ${INFO}./tools/release/prep.sh ${RELEASE} rc-v<next-version>${NC}\n"
    ;;
esac

# Watch GitHub Actions release workflows (last step)
if [ "${#RELEASE_GH_RUNS[@]}" -gt 0 ]; then
  WATCH_RUNS_SH="$(dirname "$0")/lib/watch-gh-runs.sh"
  chmod +x "$WATCH_RUNS_SH" 2>/dev/null || true
  "$WATCH_RUNS_SH" "$RELEASE" "${RELEASE_GH_RUNS[@]}"
fi

printf "Next steps: \n"
printf "  1. update example templates to point to it:\n"

# particular to my machine, but just for examples ...
if [ -d ~/code/psoxy-example-aws/ ]; then
  AWS_EXAMPLE_DIR="~/code/psoxy-example-aws"
else
  AWS_EXAMPLE_DIR="~/psoxy-example-aws"
fi

if [ -d ~/code/psoxy-example-gcp/ ]; then
  GCP_EXAMPLE_DIR="~/code/psoxy-example-gcp"
else
  GCP_EXAMPLE_DIR="~/psoxy-example-gcp"
fi

printf "    ${INFO}./tools/release/example-create-release-pr.sh . aws ${AWS_EXAMPLE_DIR}${NC}\n"
printf "    ${INFO}./tools/release/example-create-release-pr.sh . gcp ${GCP_EXAMPLE_DIR}${NC}\n"

printf "2. Update stable demo deployment to point to it. In ${INFO}psoxy-demos${NC} repo, run:\n"
printf "    ${INFO}./update-stable.sh${NC}\n"

