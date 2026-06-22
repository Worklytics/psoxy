#!/bin/bash

# usage:
# ./tools/upgrade-tf-modules.sh <next-release> <path-to-clone>
# ./tools/upgrade-tf-modules.sh v0.4.30 ~/code/psoxy/
#
# NOTE: this is called 'upgrade', but in principal it can be used to downgrade as well. But in
# general proxy terraform modules aren't built to support downgrading, so YMMV

NEXT_RELEASE=$1

# colors
COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

CURRENT_RELEASE=$(sed -n '/git::https:\/\/github\.com\/worklytics\/psoxy\//{s/.*ref=\([^"&]*\).*/\1/p;q;}' main.tf)

# if $NEXT_RELEASE is not provided, warn user and exit
if [ -z "$NEXT_RELEASE" ]; then
  printf "${ERR}Next release version not specified. Exiting.${NC}\n"
  exit 1
fi

printf "Parsed your current terraform module version as ${INFO}${CURRENT_RELEASE}${NC}; this script will upgrade it to ${SUCCESS}${NEXT_RELEASE}${NC}?\n"

read -p "Do you wish to continue? (Y/n) " -n 1 -r
REPLY=${REPLY:-Y}
echo "" # newline

case "$REPLY" in
  [yY][eE][sS]|[yY])
    echo "Applying upgrade ..."
    ;;
  [nN]|[oO])
    echo "Aborted."
    exit 0
    ;;
  *)
    printf "${ERR}Invalid input${NC}\n"
    exit 1
    ;;
esac

CURRENT_RELEASE_PATTERN=$(echo $CURRENT_RELEASE | sed 's/\./\\\./g')
PATTERN="s|ref=${CURRENT_RELEASE_PATTERN}|ref=${NEXT_RELEASE}|"

# find + sed to replace all references to the current release with the next release
find . -type f -name "*.tf" -exec sed -i.bck "${PATTERN}" {} +

# delete the sed backup files
find . -type f -name "*.bck" -delete

terraform init -upgrade

# Run check-prereqs.sh script and give user feedback
PREREQS_SCRIPT=""
if [ -d ".terraform" ]; then
  PREREQS_SCRIPT=$(find .terraform/modules -name "check-prereqs.sh" 2>/dev/null | head -n 1)
fi
if [ -z "$PREREQS_SCRIPT" ] || [ ! -f "$PREREQS_SCRIPT" ]; then
  if [ -f "$(dirname "$0")/check-prereqs.sh" ]; then
    PREREQS_SCRIPT="$(dirname "$0")/check-prereqs.sh"
  fi
fi

if [ -n "$PREREQS_SCRIPT" ] && [ -f "$PREREQS_SCRIPT" ]; then
  printf "\nRunning environment prerequisites check...\n"
  bash "$PREREQS_SCRIPT"
fi

printf "Terraform module versions upgraded to ${SUCCESS}${NEXT_RELEASE}${NC}.\n"
printf "To revert: ${INFO}$0 ${CURRENT_RELEASE}${NC}\n"

DEPLOYMENT_BUNDLE_LIB="$(dirname "$0")/lib/deployment-bundle.sh"
if [ -f "$DEPLOYMENT_BUNDLE_LIB" ]; then
  # shellcheck source=lib/deployment-bundle.sh
  source "$DEPLOYMENT_BUNDLE_LIB"
  deployment_bundle_maybe_upgrade "$NEXT_RELEASE"
else
  printf "${WARN}Warning:${NC} deployment bundle helper not found at ${DEPLOYMENT_BUNDLE_LIB}; skipping bundle update.\n"
fi

# parse NEXT_RELEASE as something of the form `rc-v0.5.6` or `v0.5.6`, as MAJOR.MINOR.PATCH
# strip optional 'rc-' and 'v' prefixes
NEXT_RELEASE_CLEAN=$(echo "$NEXT_RELEASE" | sed 's/^rc-//' | sed 's/^v//')
NEXT_MAJOR=$(echo "$NEXT_RELEASE_CLEAN" | sed 's/^\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\1/')
NEXT_MINOR=$(echo "$NEXT_RELEASE_CLEAN" | sed 's/^\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\2/')
NEXT_PATCH=$(echo "$NEXT_RELEASE_CLEAN" | sed 's/^\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\3/')

# parse CURRENT_RELEASE as something of the form `rc-v0.5.6` or `v0.5.6`, as MAJOR.MINOR.PATCH
# strip optional 'rc-' and 'v' prefixes
CURRENT_RELEASE_CLEAN=$(echo "$CURRENT_RELEASE" | sed 's/^rc-//' | sed 's/^v//')
CURRENT_MAJOR=$(echo "$CURRENT_RELEASE_CLEAN" | sed 's/^\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\1/')
CURRENT_MINOR=$(echo "$CURRENT_RELEASE_CLEAN" | sed 's/^\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\2/')
CURRENT_PATCH=$(echo "$CURRENT_RELEASE_CLEAN" | sed 's/^\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\3/')

if [[ "$NEXT_MINOR" =~ ^[0-9]+$ ]] && [[ "$CURRENT_MINOR" =~ ^[0-9]+$ ]]; then
  if [ "$NEXT_MINOR" -gt "$CURRENT_MINOR" ]; then
    printf "Next release version *may* include a provider bump. It is recommended to run ${INFO} terraform init --upgrade${NC} to get the latest versions of all terraform providers that are compatible with your configuration.\n"
    printf "You may first wish to run ${INFO}terraform providers${NC} to review the various provider version constraints, and consider revising them in top-level ${INFO}main.tf${NC} or wherever they're specified.\n"
  fi
fi

UPGRADE_GUIDE_URL="https://github.com/Worklytics/psoxy/blob/main/docs/guides/upgrading-versions.md#reviewing-your-terraform-plan"
PLAN_TIMESTAMP=$(date +%Y%m%d-%H%M%S)
PLAN_FILE="${PLAN_TIMESTAMP}-upgrade-plan.txt"

printf "\n${WARN}NOTE:${NC} No changes have yet been made to your infrastructure.\n"
printf "The updated Terraform configuration must still be applied.\n\n"

printf "Before applying, preview what will change:\n"
printf "  ${CODE}terraform plan${NC}\n\n"
printf "To save the plan for review, redirect output to a dated file:\n"
printf "  ${CODE}terraform plan -no-color > \"${PLAN_FILE}\" 2>&1${NC}\n\n"

printf "${WARN}Review the plan carefully${NC} before running ${CODE}terraform apply${NC}.\n"
printf "Consider sharing it with Worklytics support, teammates, or an LLM.\n"
printf "Full guidance: ${INFO}${UPGRADE_GUIDE_URL}${NC}\n\n"

printf "Key items to watch for:\n"
printf "  - ${ERR}Rotating/destroying the pseudonymization SALT${NC} — previously pseudonymized data will be inconsistent; restore the prior salt or re-ingest all data to Worklytics\n"
printf "  - ${ERR}Replacing Lambda/Cloud Function resources${NC} — especially their function URLs (update connections in Worklytics)\n"
printf "  - ${ERR}Replacing any -input buckets${NC} — update data pipelines that write to these buckets\n"
printf "  - ${ERR}Replacing any -sanitized buckets${NC} — update connections in Worklytics\n"
printf "  - ${ERR}Replacing parameters/secrets with API credentials${NC} — when NOT managed by this Terraform configuration (recover credentials or obtain new ones)\n"
printf "  - ${ERR}Replacing the IAM role used by Worklytics${NC} — to invoke cloud functions or read from -sanitized buckets (update connections in Worklytics)\n\n"

printf "Example LLM prompt:\n"
printf "  ${CODE}Review and summarize the output of terraform plan stored in ${PLAN_FILE}. Flag any high-risk changes, especially destruction or replacement of the pseudonymization SALT, Lambda/Cloud Function resources (and their function URLs), -input buckets, -sanitized buckets, unmanaged API credential parameters/secrets, and the IAM role Worklytics uses to invoke functions or read sanitized buckets. For each issue, explain the operational impact and what I must do before applying.${NC}\n\n"

printf "When the plan looks safe, run ${CODE}terraform apply${NC} to provision these upgrades.\n"