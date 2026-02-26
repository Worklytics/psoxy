#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# default to .terraform/modules/psoxy/ if no argument provided (this is the correct thing when
# running from the root of the psoxy repo)
PSOXY_BASE_DIR=${1:-".terraform/modules/psoxy/"}

MODULE_PATH="${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs"

if [ ! -d "$MODULE_PATH" ]; then
  printf "${ERR}Connector specs module not found at ${MODULE_PATH}.${NC}\n"
  printf "(if testing from root of main psoxy repo, run this script as ${INFO}./tools/available-connectors.sh ./${NC})\n"
  printf "(if testing from inside example, run this script as ${INFO}../../../tools/available-connectors.sh ../../../${NC})\n"
  printf "Exiting.${NC}\n"
  exit 1
fi

# init worklytics-connector-specs module as if it's a terraform config, so subsequent 'console' call
# will work
terraform -chdir="${MODULE_PATH}" init >> /dev/null
CLI_VARS="-var=include_msft=true -var=include_google_workspace=true"
AVAILABLE_CONNECTORS=$(echo "jsonencode(tolist(keys(local.all_default_connectors)))" | terraform -chdir="${MODULE_PATH}" console $CLI_VARS)

# clean up what the init did above
rm -rf "${MODULE_PATH}/.terraform" 2> /dev/null
rm "${MODULE_PATH}/.terraform.lock.hcl" 2> /dev/null

if [ -z "$AVAILABLE_CONNECTORS" ]; then
  printf "${ERR}Failed to generate list of available connectors${NC} Contact support for assistance.\n"
else
  printf "The following connector configurations are available for the current version of the proxy Terraform modules you're using:\n"
  echo "$AVAILABLE_CONNECTORS" | jq -r 'fromjson | .'

  printf "To use a connector, add its id from the above list to ${INFO}enabled_connectors${NC} in your ${INFO}terraform.tfvars${NC} file.\n";
  printf "Review the documentation for the connector at ${INFO}https://docs.worklytics.co/psoxy/sources${NC} for more information.\n"
fi
