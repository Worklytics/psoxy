#!/bin/bash

# fills a terraform vars file with values that are required for the Terraform configuration to work

TFVARS_FILE=$1
PSOXY_BASE_DIR=$2

RELEASE_VERSION="v0.4.24"

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

printf "# terraform.tfvars\n" >> $TFVARS_FILE
printf "# this file sets the values of variables for your Terraform configuration. You should manage it under \n" >> $TFVARS_FILE
printf "# version control. anyone working with the infrastructure created by this Terraform configuration will need it\n" >> $TFVARS_FILE
printf "# -- initialized with ${RELEASE_VERSION} of tools/init-tfvars.sh -- \n\n" >> $TFVARS_FILE

echo "# root directory of a clone of the psoxy repo " >> $TFVARS_FILE
echo "#  - by default, it points to .terraform, where terraform clones the main psoxy repo" >> $TFVARS_FILE
echo "#  - if you have a local clone of the psoxy repo you prefer to use, change this to point there" >> $TFVARS_FILE
printf "psoxy_base_dir = \"${PSOXY_BASE_DIR}\"\n" >> $TFVARS_FILE
printf "provision_testing_infra = true\n" >> $TFVARS_FILE
printf "\n" >> $TFVARS_FILE

# pattern used to grep for provider at top-level of Terraform configuration
TOP_LEVEL_PROVIDER_PATTERN="^├── provider\[registry.terraform.io/hashicorp"

AWS_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/aws" | wc -l)
if test $AWS_PROVIDER_COUNT -ne 0; then
  printf "AWS provider in Terraform configuration. Initializing variables it requires ...\n"
  if aws --version &> /dev/null; then

    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    printf "# AWS account in which your Psoxy instances will be deployed\n" >> $TFVARS_FILE
    printf "aws_account_id=\"${AWS_ACCOUNT_ID}\"\n\n" >> $TFVARS_FILE
    printf "\taws_account_id=${BLUE}\"${AWS_ACCOUNT_ID}\"${NC}\n"

    AWS_REGION=$(aws configure get region)
    printf "# AWS region in which your Psoxy infrastructure will be deployed\n" >> $TFVARS_FILE
    printf "aws_region=\"${AWS_REGION}\"\n\n" >> $TFVARS_FILE
    printf "\taws_region=${BLUE}\"${AWS_REGION}\"${NC}\n"

    AWS_ARN=$(aws sts get-caller-identity --query Arn --output text)
    printf "# AWS IAM role to assume when deploying your Psoxy infrastructure via Terraform, if needed\n" >> $TFVARS_FILE
    printf "# - this variable is used when you are authenticated as an AWS user which can assume the AWS role which actually has the requisite permissions to provision your infrastructure\n" >> $TFVARS_FILE
    printf "#   (this is approach is good practice, as minimizes the privileges of the AWS user you habitually use and easily supports multi-account scenarios) \n" >> $TFVARS_FILE
    printf "# - if you are already authenticated as a sufficiently privileged AWS Principal, you can omit this variable\n" >> $TFVARS_FILE
    printf "# - often, this will be the default 'super-admin' role in the target AWS account, eg something like 'arn:aws:iam::123456789012:role/Admin'\n" >> $TFVARS_FILE
    printf "# - see https://github.com/Worklytics/psoxy/blob/${RELEASE_VERSION}/docs/aws/getting-started.md for details on required permissions\n" >> $TFVARS_FILE
    printf "# aws_assume_role_arn=\"${AWS_ARN}\" #(double-check this; perhaps needs to be a role within target account) \n\n" >> $TFVARS_FILE

    printf "# AWS principals in the following list will be explicitly authorized to invoke your proxy instances\n" >> $TFVARS_FILE
    printf "#  - this is for initial testing/development; it can (and should) be empty for production-use\n" >> $TFVARS_FILE
    printf "caller_aws_arns = [\n  \"${AWS_ARN}\" # for testing; can remove once prod-ready\n]\n\n" >> $TFVARS_FILE

    printf "# GCP service accounts with ids in the list below will be allowed to invoke your proxy instances\n" >> $TFVARS_FILE
    printf "#  - for initial testing/deployment, it can be empty list; it needs to be filled only once you're ready to authorize Worklytics to access your data\n" >> $TFVARS_FILE
    printf "caller_gcp_service_account_ids = [\n " >> $TFVARS_FILE
    printf "  # put 'Service Account Unique ID' value, which you can obtain from Worklytics ( https://intl.worklytics.co/analytics/integrations/configuration )\n" >> $TFVARS_FILE
    printf "  # \"123456712345671234567\" # should be 21-digits\n]\n\n" >> $TFVARS_FILE
  else
    printf "${RED}AWS CLI not available${NC}\n"
  fi
else
  printf "No AWS provider found in top-level of Terraform configuration. AWS CLI not required.\n"
fi


# GCP / Google Workspace (google provider used for both)
GOOGLE_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/google" | wc -l)
if test $GOOGLE_PROVIDER_COUNT -ne 0; then
  printf "Google provider in Terraform configuration. Initializing variables it requires ...\n"
  if gcloud --version &> /dev/null ; then

    # project
    printf "# GCP project in which required infrastructure will be provisioned\n" >> $TFVARS_FILE
    printf "#  - if you're deploying to AWS and not connecting to Google Workspace data sources, you can omit this value\n" >> $TFVARS_FILE
    GCP_PROJECT_ID=$(gcloud config get project)
    printf "gcp_project_id=\"${GCP_PROJECT_ID}\"\n\n" >> $TFVARS_FILE
    printf "\tgcp_project_id=${BLUE}\"${GCP_PROJECT_ID}\"${NC}\n"

    # tenant SA emails
    printf "# GCP service account emails in the list below will be allowed to invoke your proxy instances\n" >> $TFVARS_FILE
    printf "#  - if you're deploying to AWS rather than GCP, you can omit this value\n" >> $TFVARS_FILE
    printf "#  - for initial testing/deployment, it can be empty list; it needs to be filled only once you're ready to authorize Worklytics to access your data\n" >> $TFVARS_FILE
    printf "worklytics_sa_emails=[\n" >> $TFVARS_FILE
    printf "  # put 'Service Account Email' value here, which you can obtain from Worklytics ( https://intl.worklytics.co/analytics/integrations/configuration )\n" >> $TFVARS_FILE
    printf "]\n\n" >> $TFVARS_FILE

    # example user for Google Workspace
    printf "# Google Workspace example user \n" >> $TFVARS_FILE
    printf "#  - this is used to aid testing of Google Workspace connectors against a real account (eg, your own); if you're not using those, it can be omitted\n" >> $TFVARS_FILE
    GOOGLE_WORKSPACE_EXAMPLE_USER=$(gcloud config get account)
    printf "google_workspace_example_user=\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"\n\n" >> $TFVARS_FILE
    printf "\tgoogle_workspace_example_user=${BLUE}\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"${NC}\n"
  else
    printf "${RED}gcloud not available${NC}\n"
  fi
else
  printf "No Google provider found in top-level of Terraform configuration. No gcloud initialization required.\n"
fi

# Microsoft 365
AZUREAD_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/azuread" | wc -l)
if test $AZUREAD_PROVIDER_COUNT -ne 0; then
  printf "AzureAD provider in Terraform configuration. Initializing variables it requires ...\n"
  if az --version &> /dev/null ; then

    printf "# Azure AD Apps (Microsoft API Clients) will be provisioned in the following tenant to access your Microsoft 365 data\n" >> $TFVARS_FILE
    printf "#  - this should be the ID of your Microsoft 365 organization (tenant)\n" >> $TFVARS_FILE
    printf "#  - if you're not connecting to Microsoft 365 data sources, you can omit this value\n" >> $TFVARS_FILE
    MSFT_TENANT_ID=$(az account show --query tenantId --output tsv)
    printf "msft_tenant_id=\"${MSFT_TENANT_ID}\"\n\n" >> $TFVARS_FILE
    printf "\tmsft_tenant_id=${BLUE}\"${MSFT_TENANT_ID}\"${NC}\n"

    MSFT_USER_EMAIL=$(az account show --query user.name --output tsv)
    printf "# users in the following list will be set as the 'owners' of the Azure AD Apps (API clients) provisioned to access your Microsoft 365 data\n" >> $TFVARS_FILE
    printf "#  - if you're not connecting to Microsoft 365 data sources, you can omit this value\n" >> $TFVARS_FILE
    printf "msft_owners_email=[\n  \"${MSFT_USER_EMAIL}\"\n]\n\n" >> $TFVARS_FILE
    printf "\tmsft_owners_email=${BLUE}[ \"${MSFT_USER_EMAIL}\" ]${NC}\n"
  else
    printf "${RED}az not available${NC}\n"
  fi
else
  printf "No Azure provider found in top-level of Terraform configuration. No Azure CLI initialization needed.\n"
fi

# initialize `enabled_connectors` variable
# NOTE: could be conditional based on google workspace, azure, etc - but as we expect future
# examples to cover ALL connectors, and just vary by host platform, we'll just initialize all for
# now and expect customers to remove them as needed
AVAILABLE_CONNECTORS=$(echo "local.available_connector_ids" | terraform -chdir="${PSOXY_BASE_DIR}infra/modules/worklytics-connector-specs" console)
printf "# review following list of connectors to enable, and comment out what you don't want\n" >> $TFVARS_FILE
printf "enabled_connectors = ${AVAILABLE_CONNECTORS}\n\n" >> $TFVARS_FILE

# give user some feedback
printf "Initialized example terraform vars file. Please open ${BLUE}${TFVARS_FILE}${NC} and customize it to your needs.\n"
printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n\n"
