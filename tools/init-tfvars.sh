#!/bin/bash

# fills a terraform vars file with values that are required for the Terraform configuration to work

TFVARS_FILE=$1
PSOXY_BASE_DIR=$2
DEPLOYMENT_ENV=${3:-"local"}
HOST_PLATFORM=${4:-"aws"}

SCRIPT_VERSION="rc-v0.5.16"

if [ -z "$PSOXY_BASE_DIR" ]; then
  printf "Usage: init-tfvars.sh <path-to-terraform.tfvars> <path-to-psoxy-base-directory> [DEPLOYMENT_ENV]\n"
  exit 1
fi

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Validate DEPLOYMENT_ENV
VALID_DEPLOYMENT_ENVS=("local" "terraform_cloud" "github_actions" "other_nonlocal")
if [[ ! " ${VALID_DEPLOYMENT_ENVS[@]} " =~ " ${DEPLOYMENT_ENV} " ]]; then
  printf "${RED}Error: Invalid DEPLOYMENT_ENV '${DEPLOYMENT_ENV}'.${NC}\n"
  printf "Valid values are: ${BLUE}local${NC}, ${BLUE}terraform_cloud${NC}, ${BLUE}github_actions${NC}, ${BLUE}other_nonlocal${NC}\n"
  exit 1
fi

prompt_user_Yn() {
  local prompt_message="$1"
  printf "$prompt_message"
  read -p "(Y/n): " yn

  # Default to 'Yes' if the input is empty
  if [ -z "$yn" ]; then
    yn="y"
  fi

  # Convert input to lowercase to be case insensitive
  yn=$(echo "$yn" | tr '[:upper:]' '[:lower:]')

  # Set result based on user input, defaulting to 'no' for any input other than 'y' or 'yes'
  result=0
  if [[ $yn == "y" || $yn == "yes" ]]; then
      result=1
  fi

  # Return the result
  return $result
}

prompt_confirm_variable_setting() {
  local setting_name="$1"
  local default_value="$2"

  # redirect output to stderr; let us capture stdout as a return value
  >&2 printf "Do you want to set ${BLUE}${setting_name}${NC} to ${BLUE}${default_value}${NC}"
  >&2 read -p "? (Y/n) " yn

  # Default to 'Yes' if the input is empty
  if [ -z "$yn" ]; then
    yn="y"
  fi

  # Convert input to lowercase to be case insensitive
  yn=$(echo "$yn" | tr '[:upper:]' '[:lower:]')

  # Set result based on user input, defaulting to 'no' for any input other than 'y' or 'yes'
  if [[ $yn == "y" || $yn == "yes" ]]; then
    result="$default_value"
  else
    >&2 printf "Enter value for ${BLUE}${setting_name}${NC}: "
    >&2 read -p " " user_provided_value
    result="$user_provided_value"
  fi

  echo "$result"
}


printf "# terraform.tfvars\n" >> $TFVARS_FILE
printf "# this file sets the values of variables for your Terraform configuration. You should manage it under \n" >> $TFVARS_FILE
printf "# version control. anyone working with the infrastructure created by this Terraform configuration will need it\n" >> $TFVARS_FILE
printf "# -- initialized with ${SCRIPT_VERSION} of tools/init-tfvars.sh -- \n\n" >> $TFVARS_FILE

echo "# root directory of a clone of the psoxy repo " >> $TFVARS_FILE
echo "#  - by default, it points to .terraform, where terraform clones the main psoxy repo" >> $TFVARS_FILE
echo "#  - if you have a local clone of the psoxy repo you prefer to use, change this to point there" >> $TFVARS_FILE
printf "psoxy_base_dir = \"${PSOXY_BASE_DIR}\"\n" >> $TFVARS_FILE

printf "\n" >> $TFVARS_FILE

# pattern used to grep for provider at top-level of Terraform configuration
TOP_LEVEL_PROVIDER_PATTERN="^├── provider\[registry.terraform.io/hashicorp"

AWS_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/aws" | wc -l)

if test $AWS_PROVIDER_COUNT -ne 0; then
  printf "AWS provider in Terraform configuration. Initializing variables it requires ...\n"
  if aws --version &> /dev/null; then


    printf "# AWS account in which your Psoxy instances will be deployed\n" >> $TFVARS_FILE
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    if [ $? -eq 0 ] && [ -n "$AWS_ACCOUNT_ID" ]; then
      user_value=$(prompt_confirm_variable_setting "aws_account_id" "$AWS_ACCOUNT_ID")
      printf "aws_account_id=\"${user_value}\"\n\n" >> $TFVARS_FILE
      printf "\taws_account_id=${BLUE}\"${user_value}\"${NC}\n"
    else
      printf "${RED}Failed to determine AWS account ID from your aws CLI configuration. You MUST fill ${BLUE}aws_account_id${NC} in your terraform.tfvars file yourself.${NC}\n"
      printf "aws_account_id=\"{{FILL_YOUR_VALUE}}\"\n\n" >> $TFVARS_FILE
    fi


    AWS_REGION=$(aws configure get region)

    if [ $? -eq 0 ] && [ -n "$AWS_REGION" ]; then
      printf "# AWS region in which your Psoxy infrastructure will be deployed\n" >> $TFVARS_FILE
      printf "aws_region=\"${AWS_REGION}\"\n\n" >> $TFVARS_FILE
      printf "\taws_region=${BLUE}\"${AWS_REGION}\"${NC}\n"
    else
      printf "No ${BLUE}aws_region${NC} could be determined from your AWS CLI configuration. You should fill ${BLUE}aws_region${NC} in your terraform.tfvars file if you wish to use a value other than the default.\n"
    fi

    AWS_ARN=$(aws sts get-caller-identity --query Arn --output text 2>/dev/null)
    AWS_ARN_EXIT_CODE=$?
    
    # Determine what to put in caller_aws_arns
    if [ $AWS_ARN_EXIT_CODE -eq 0 ] && [ -n "$AWS_ARN" ]; then
      # AWS CLI is authenticated and returned a valid ARN
      TEST_AWS_ARN="\"${AWS_ARN}\" # for testing; can remove once ready for production\n"
    else
      # AWS CLI not authenticated or returned empty - use empty list (no empty string)
      TEST_AWS_ARN=""
    fi
    
    # Set AWS_ARN for aws_assume_role_arn comment (even if empty)
    if [ $AWS_ARN_EXIT_CODE -eq 0 ] && [ -z "$AWS_ARN" ]; then
      AWS_ARN="{{ARN_OF_AWS_ROLE_TERRAFORM_SHOULD_ASSUME}}"
    fi
    
    printf "# AWS IAM role to assume when deploying your Psoxy infrastructure via Terraform, if needed\n" >> $TFVARS_FILE
    printf "# - this variable is used when you are authenticated as an AWS user which can assume the AWS role which actually has the requisite permissions to provision your infrastructure\n" >> $TFVARS_FILE
    printf "#   (this is approach is good practice, as minimizes the privileges of the AWS user you habitually use and easily supports multi-account scenarios) \n" >> $TFVARS_FILE
    printf "# - if you are already authenticated as a sufficiently privileged AWS Principal, you can omit this variable\n" >> $TFVARS_FILE
    printf "# - often, this will be the default 'super-admin' role in the target AWS account, eg something like 'arn:aws:iam::123456789012:role/Admin'\n" >> $TFVARS_FILE
    printf "# - see https://github.com/Worklytics/psoxy/blob/v${RELEASE_VERSION}/docs/aws/getting-started.md for details on required permissions\n" >> $TFVARS_FILE
    printf "# aws_assume_role_arn=\"${AWS_ARN}\" #(double-check this; perhaps needs to be a role within target account) \n\n" >> $TFVARS_FILE

    printf "# AWS principals in the following list will be explicitly authorized to invoke your proxy instances\n" >> $TFVARS_FILE
    printf "#  - this is for initial testing/development; it can (and should) be empty for production-use\n" >> $TFVARS_FILE
    if [ -n "$TEST_AWS_ARN" ]; then
      printf "caller_aws_arns = [\n  ${TEST_AWS_ARN}]\n\n" >> $TFVARS_FILE
    else
      printf "caller_aws_arns = [\n]\n\n" >> $TFVARS_FILE
    fi

    printf "# GCP service accounts with ids in the list below will be allowed to invoke your proxy instances\n" >> $TFVARS_FILE
    printf "#  - for initial testing/deployment, it can be empty list; it needs to be filled only once you're ready to authorize Worklytics to access your data\n" >> $TFVARS_FILE
    printf "caller_gcp_service_account_ids = [\n " >> $TFVARS_FILE
    printf "  # put 'Service Account Unique ID' value, which you can obtain from Worklytics ( https://intl.worklytics.co/analytics/integrations/configuration )\n" >> $TFVARS_FILE
    printf "  # \"123456712345671234567\" # should be 21-digits\n]\n\n" >> $TFVARS_FILE
  else
    printf "${RED}AWS CLI not available${NC}\n"
  fi
else
  if [[ "$HOST_PLATFORM" == "aws" ]]; then
    printf "${RED}HOST_PLATFORM set as 'aws', but no aws provider in Terraform configuration${NC}\n"
  else
    printf "No AWS provider found in top-level of Terraform configuration. AWS CLI not required.\n"
  fi
fi


# GCP / Google Workspace (google provider used for both)
remove_google_workspace() {

  if [[ -f google-workspace-variables.tf ]]; then
    rm google-workspace-variables.tf
  fi

  if [[ -f google-workspace.tf ]]; then
    rm google-workspace.tf
  fi

  sed -i '' '/^[[:space:]]*module\.worklytics_connectors_google_workspace\.enabled_api_connectors,[[:space:]]*$/d' main.tf

  sed -i '' '/^[[:space:]]*module\.worklytics_connectors_google_workspace\.todos,[[:space:]]*$/d' main.tf

  sed -i '' '/^[[:space:]]*module\.worklytics_connectors_google_workspace\.next_todo_step,[[:space:]]*$/d' main.tf
}

INCLUDE_GWS="false"
GOOGLE_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/google" | wc -l)
if test $GOOGLE_PROVIDER_COUNT -ne 0; then
  if gcloud --version &> /dev/null ; then

    # project

    GCP_PROJECT_ID=$(gcloud config get project)

    if [[ "$HOST_PLATFORM" == "gcp" ]]; then
      [[ -f variables.tf ]] && grep -q '^variable "gcp_project_id"' variables.tf
      if [[ $? -eq 0 ]]; then
        printf "# GCP project in which required infrastructure will be provisioned\n" >> $TFVARS_FILE
        printf "gcp_project_id=\"${GCP_PROJECT_ID}\"\n\n" >> $TFVARS_FILE
        printf "\tgcp_project_id=${BLUE}\"${GCP_PROJECT_ID}\"${NC}\n"
      fi

      # tenant SA emails
      [[ -f variables.tf ]] && grep -q '^variable "worklytics_sa_emails"' variables.tf
      if [[ $? -eq 0 ]]; then
        printf "# GCP service account emails in the list below will be allowed to invoke your proxy instances\n" >> $TFVARS_FILE
        printf "#  - NOTE: this value only applies to GCP deployments\n" >> $TFVARS_FILE
        printf "#  - for initial testing/deployment, it can be empty list; it needs to be filled only once you're ready to authorize Worklytics to access your data\n" >> $TFVARS_FILE
        printf "worklytics_sa_emails=[\n" >> $TFVARS_FILE
        printf "  # put 'Service Account Email' value here, which you can obtain from Worklytics ( https://intl.worklytics.co/analytics/integrations/configuration )\n" >> $TFVARS_FILE
        printf "]\n\n" >> $TFVARS_FILE
      fi
    fi

    prompt_user_Yn "Do you want to use ${BLUE}Google Workspace${NC} as a data source? (requires ${BLUE}gcloud${NC} to be installed and authenticated in the environment from which this terraform configuration will be applied) "

    if [[ $? -eq 1 ]]; then
      INCLUDE_GWS="true"
      # init google workspace variables if file exists OR the variables are in the main variables.tf file
      # (google_workspace_gcp_project_id not in all legacy examples)
      [[ -f google-workspace-variables.tf ]] || grep -q '^variable "google_workspace_gcp_project_id"' variables.tf
      if [[ $? -eq 0 ]]; then
        printf "# GCP project in which OAuth clients for Google Workspace connectors will be provisioned\n" >> $TFVARS_FILE
        printf "#  - if you're not connecting to Google Workspace data sources via their APIs, you can omit this value\n" >> $TFVARS_FILE
        printf "google_workspace_gcp_project_id=\"${GCP_PROJECT_ID}\"\n\n" >> $TFVARS_FILE
        printf "\tgoogle_workspace_gcp_project_id=${BLUE}\"${GCP_PROJECT_ID}\"${NC}\n"
      fi

      # init google workspace variables if file exists OR the variables are in the main variables.tf file
      [[ -f google-workspace-variables.tf ]] || grep -q '^variable "google_workspace_example_user"' variables.tf
      if [[ $? -eq 0 ]]; then
        # example user for Google Workspace
        printf "# Google Workspace example user \n" >> $TFVARS_FILE
        printf "#  - this is used to aid testing of Google Workspace connectors against a real account (eg, your own); if you're not using those, it can be omitted\n" >> $TFVARS_FILE
        GOOGLE_WORKSPACE_EXAMPLE_USER=$(gcloud config get account)
        printf "google_workspace_example_user=\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"\n\n" >> $TFVARS_FILE
        printf "\tgoogle_workspace_example_user=${BLUE}\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"${NC}\n"

        # example admin for Google Workspace
        printf "# Google Workspace example admin \n" >> $TFVARS_FILE
        printf "#  - this is used to aid testing of Google Workspace connectors against a real account, in cases where an admin is explicitly required\n" >> $TFVARS_FILE
        GOOGLE_WORKSPACE_EXAMPLE_USER=$(gcloud config get account)
        printf "google_workspace_example_admin=\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"\n\n" >> $TFVARS_FILE
        printf "\tgoogle_workspace_example_admin=${BLUE}\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"${NC}\n"
      fi
    else
      remove_google_workspace
    fi
  else
    printf "${RED}gcloud not available. Your configuration will likely not run. ${NC}\n"
  fi
else
  printf "No Google provider found in top-level of Terraform configuration. No gcloud initialization required.\n"

  if [ -f google-workspace.tf ]; then
    printf "If you don't intend to use Google Workspace via APIs as a data source in future, you can ${BLUE}rm google-workspace.tf${NC} and ${BLUE}rm google-workspace-variables.tf${NC} \n"
  fi
fi

remove_msft() {
  if [[ -f msft-365-variables.tf ]]; then
    rm msft-365-variables.tf
  fi

  if [[ -f msft-365.tf ]]; then
    rm msft-365.tf
  fi

  sed -i '' '/^[[:space:]]*local\.msft_api_connectors_with_auth,[[:space:]]*$/d' main.tf

  sed -i '' '/^[[:space:]]*module\.worklytics_connectors_msft_365\.todos,[[:space:]]*$/d' main.tf

  sed -i '' '/^[[:space:]]*module\.worklytics_connectors_msft_365\.next_todo_step,[[:space:]]*$/d' main.tf
}

# Microsoft 365
AZUREAD_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/azuread" | wc -l)
INCLUDE_MSFT="false"
if test $AZUREAD_PROVIDER_COUNT -ne 0; then
  printf "AzureAD provider in Terraform configuration.\n"
  MSFT_VARIABLES_DEFINED=$( [[ -f msft-365-variables.tf ]] || grep -q '^variable "msft_tenant_id"' variables.tf)

  if $MSFT_VARIABLES_DEFINED; then
    prompt_user_Yn "Do you want to use ${BLUE}Microsoft 365${NC} as a data source? (requires ${BLUE}az${NC} CLI to be installed and authenticated in the environment from which this terraform configuration will be applied) "

    if [[ $? -eq 1 ]]; then
      if az --version &> /dev/null ; then
        printf "Azure CLI already installed.\n"

        printf "# Azure AD Apps (Microsoft API Clients) will be provisioned in the following tenant to access your Microsoft 365 data\n" >> $TFVARS_FILE
        printf "#  - this should be the ID of your Microsoft 365 organization (tenant)\n" >> $TFVARS_FILE
        printf "#  - if you're not connecting to Microsoft 365 data sources via their APIs, you can omit this value\n" >> $TFVARS_FILE
        MSFT_TENANT_ID=$(az account show --query tenantId --output tsv)
        printf "msft_tenant_id=\"${MSFT_TENANT_ID}\"\n\n" >> $TFVARS_FILE
        printf "\tmsft_tenant_id=${BLUE}\"${MSFT_TENANT_ID}\"${NC}\n"

        MSFT_USER_EMAIL=$(az account show --query user.name --output tsv)
        printf "# users in the following list will be set as the 'owners' of the Azure AD Apps (API clients) provisioned to access your Microsoft 365 data\n" >> $TFVARS_FILE
        printf "#  - if you're not connecting to Microsoft 365 data sources, you can omit this value\n" >> $TFVARS_FILE
        printf "msft_owners_email=[\n  \"${MSFT_USER_EMAIL}\"\n]\n\n" >> $TFVARS_FILE
        printf "\tmsft_owners_email=${BLUE}[ \"${MSFT_USER_EMAIL}\" ]${NC}\n"
        INCLUDE_MSFT="true"
      else
        printf "${RED}az not available${NC}. Microsoft 365 variables cannot be initialized.\n"
      fi
    else
      remove_msft
    fi
  else
    printf "No Microsoft 365 variables defined in configuration.\n"
  fi
else
  printf "No Azure provider found in top-level of Terraform configuration. No Azure CLI initialization needed.\n"
fi

# initialize `enabled_connectors` variable
# NOTE: could be conditional based on google workspace, azure, etc - but as we expect future
# examples to cover ALL connectors, and just vary by host platform, we'll just initialize all for
# now and expect customers to remove them as needed

# init worklytics-connector-specs module as if it's a terraform config, so subsequent 'console' call
# will work
terraform -chdir="${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs" init >> /dev/null
CLI_VARS="-var=include_msft=${INCLUDE_MSFT} -var=include_google_workspace=${INCLUDE_GWS}"
DEFAULT_CONNECTORS_TO_ENABLE=$(echo "local.default_enabled_connector_ids" | terraform -chdir="${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs" console $CLI_VARS)
ALL_AVAILABLE_CONNECTORS=$(echo "jsonencode(tolist(keys(local.all_default_connectors)))" | terraform -chdir="${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs" console $CLI_VARS)

# clean up what the init did above
rm -rf "${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs/.terraform" 2> /dev/null
rm "${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs/.terraform.lock.hcl" 2> /dev/null

if [ -z "$DEFAULT_CONNECTORS_TO_ENABLE" ]; then
  printf "${RED}Failed to generate list of enabled_connectors${NC}; you will need to add an variable assigned for ${BLUE}enabled_connectors${NC} to your ${BLUE}terraform.tfvars${NC} as a list of connector ID strings. Contact support for assistance.\n"
else
  printf "# review following list of connectors below to enable, and comment out what you don't want\n" >> $TFVARS_FILE
  printf "# NOTE: usage of some connectors may require specific license from Worklytics or the data source; or have a usage cost on the data source side. Worklytics is not responsible for any costs incurred on the data source side or by usage of the APIs it provides.\n" >> $TFVARS_FILE
  printf "enabled_connectors = ${DEFAULT_CONNECTORS_TO_ENABLE}\n\n" >> $TFVARS_FILE
fi

# if ALL_AVAILABLE_CONNECTORS is not empty, then list them in terraform.tfvars
if [[ -n "$ALL_AVAILABLE_CONNECTORS" ]]; then
  printf "# If you wish to enable additional connectors, you can uncomment and add one of the ids\n" >> $TFVARS_FILE
  printf "# listed below  to \`enabled_connectors\`; run \`./available-connectors\` if available for an updated list\n" >> $TFVARS_FILE
  
  # Parse JSON array and output one connector per line, each commented
  # ALL_AVAILABLE_CONNECTORS is a JSON-encoded string from terraform console: "[\"asana\",\"azure-ad\",...]"
  # First decode the JSON string, then parse the array
  if command -v jq &> /dev/null; then
    # Use jq to decode the JSON string and then parse the array
    echo "$ALL_AVAILABLE_CONNECTORS" | jq -r '.' 2>/dev/null | jq -r '.[]' 2>/dev/null | while read -r connector; do
      if [ -n "$connector" ]; then
        printf "#   \"%s\",\n" "$connector" >> $TFVARS_FILE
      fi
    done
  else
    # Manual parsing: terraform outputs "[\"asana\",\"azure-ad\",\"badge\"]"
    # Remove outer quotes, then parse the inner JSON array
    echo "$ALL_AVAILABLE_CONNECTORS" | sed 's/^"//' | sed 's/"$//' | sed 's/\\"/"/g' | sed 's/^\[//' | sed 's/\]$//' | tr ',' '\n' | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//' | sed 's/^"//' | sed 's/"$//' | while read -r connector; do
      if [ -n "$connector" ]; then
        printf "#   \"%s\",\n" "$connector" >> $TFVARS_FILE
      fi
    done
  fi
  
  printf "\n" >> $TFVARS_FILE
fi

printf "\n"

if [ "$HOST_PLATFORM" == "aws" ]; then
  # AWS testing requires provisioning to IAM perms, for which GCP case doesn't support equivalent
  # atm
  printf "provision_testing_infra = true\n" >> $TFVARS_FILE
fi


if [ "$DEPLOYMENT_ENV" != "local" ]; then
  printf "Setting ${BLUE}install_test_tool=false${NC} and ${BLUE}todos_as_outputs=true${NC}, because your ${BLUE}terraform apply${NC} will run remotely.\n\n"

  echo "install_test_tool = false" >> $TFVARS_FILE
  echo "todos_as_outputs = true" >> $TFVARS_FILE
fi

# Check for published bundles and offer to use them
check_and_offer_published_bundle() {
  local version=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "${PSOXY_BASE_DIR}java/pom.xml")
  if [ -z "$version" ]; then
    return 0  # Can't determine version, skip check
  fi

  local bundle_path=""
  local bundle_exists=false

  if [ "$HOST_PLATFORM" == "aws" ]; then
    if ! command -v aws &> /dev/null; then
      return 0  # AWS CLI not installed, skip check
    fi

    # Get AWS region from terraform.tfvars if set, otherwise from AWS config, or use default
    local aws_region=""
    if grep -q '^[[:space:]]*aws_region[[:space:]]*=' "$TFVARS_FILE" 2>/dev/null; then
      aws_region=$(grep '^[[:space:]]*aws_region[[:space:]]*=' "$TFVARS_FILE" | sed 's/.*=[[:space:]]*"\([^"]*\)".*/\1/' | head -1)
    fi
    if [ -z "$aws_region" ]; then
      aws_region=$(aws configure get region 2>/dev/null || echo "")
    fi
    if [ -z "$aws_region" ]; then
      aws_region="us-east-1"  # Default region
    fi

    local bucket_name="psoxy-public-artifacts-${aws_region}"
    local jar_name="psoxy-aws-${version}.jar"
    local s3_path="s3://${bucket_name}/${jar_name}"

    # Check if bundle exists in S3
    if aws s3 ls "$s3_path" >/dev/null 2>&1; then
      bundle_path="$s3_path"
      bundle_exists=true
    fi
  elif [ "$HOST_PLATFORM" = "gcp" ]; then
    if ! command -v gsutil &> /dev/null; then
      return 0  # gsutil not installed, skip check
    fi

    local bucket_name="psoxy-public-artifacts"
    local zip_name="psoxy-gcp-${version}.zip"
    local gcs_path="gs://${bucket_name}/${zip_name}"

    # Check if bundle exists in GCS
    if gsutil ls "$gcs_path" >/dev/null 2>&1; then
      bundle_path="$gcs_path"
      bundle_exists=true
    fi
  fi

  if [ "$bundle_exists" = true ]; then
    printf "\n"
    printf "Found published deployment bundle for version ${BLUE}${version}${NC} at:\n"
    printf "  ${GREEN}${bundle_path}${NC}\n"
    prompt_user_Yn "Do you want to use this published bundle instead of building one locally?"
    if [[ $? -eq 1 ]]; then
      # User wants to use published bundle
      if grep -q '^[[:space:]]*deployment_bundle' "$TFVARS_FILE" 2>/dev/null; then
        sed -i.bck "/^[[:space:]]*deployment_bundle.*/c\\
deployment_bundle = \"${bundle_path}\"" "$TFVARS_FILE"
        rm -f "${TFVARS_FILE}.bck" 2>/dev/null
      else
        printf "deployment_bundle = \"${bundle_path}\"\n\n" >> $TFVARS_FILE
      fi
      printf "Set ${BLUE}deployment_bundle${NC} to ${GREEN}${bundle_path}${NC}\n"
      return 1  # Indicate bundle was set
    fi
  fi

  return 0
}

# Check for published bundles (only if not terraform_cloud, as that needs local build)
if [ "$DEPLOYMENT_ENV" != "terraform_cloud" ]; then
  check_and_offer_published_bundle
  bundle_was_set=$?
fi

if [ "$DEPLOYMENT_ENV" == "terraform_cloud" ]; then
  # need to build the JAR now, to ship with the proxy
  ${PSOXY_BASE_DIR}tools/update-bundle.sh $PSOXY_BASE_DIR $TFVARS_FILE $HOST_PLATFORM
fi

[[ -f variables.tf ]] && grep -q '^variable "environment_name"' variables.tf
if [[ $? -eq 0 ]]; then
  printf "# environment_name is used to name resources provisioned by this Terraform configuration\n" >> $TFVARS_FILE
  printf "environment_name =\"psoxy\"\n\n" >> $TFVARS_FILE
fi

printf "\n\n"

# give user some feedback
printf "Initialized example terraform vars file. Please open ${BLUE}${TFVARS_FILE}${NC} and customize it to your needs.\n"
printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n\n"
