#!/bin/bash

# fills a terraform vars file with values that are required for the Terraform configuration to work

TFVARS_FILE=$1
PSOXY_BASE_DIR=$2

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# append root of checkout automatically
echo "# this points to the directory where Java source to be compiled into deployment JAR is located" >> $TFVARS_FILE
echo "# by default, it points to .terraform, where terraform clones the main psxoy repo" >> $TFVARS_FILE
echo "# if you have a local clone of the psoxy repo you prefer to use, change this to point there" >> $TFVARS_FILE
echo "psoxy_base_dir = \"${PSOXY_BASE_DIR}\"" >> $TFVARS_FILE

# pattern used to grep for provider at top-level of Terraform configuration
TOP_LEVEL_PROVIDER_PATTERN="^├── provider\[registry.terraform.io/hashicorp"

AWS_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/aws" | wc -l)
if test $AWS_PROVIDER_COUNT -ne 0; then
  printf "AWS provider in Terraform configuration. Initializing variables it requires ...\n"
  if aws --version &> /dev/null; then
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    AWS_ARN=$(aws sts get-caller-identity --query Arn --output text)
    AWS_REGION=$(aws configure get region)
    echo "aws_account_id=\"${AWS_ACCOUNT_ID}\"" >> $TFVARS_FILE
    printf "\taws_account_id=${BLUE}\"${AWS_ACCOUNT_ID}\"${NC}\n"
    echo "aws_region=\"${AWS_REGION}\"" >> $TFVARS_FILE
    printf "\taws_region=${BLUE}\"${AWS_REGION}\"${NC}\n"
    printf "aws_assume_role_arn=\"${AWS_ARN}\" #(double-check this; perhaps needs to be a role within target account) \n" >> $TFVARS_FILE
    printf "\taws_assume_role_arn=${BLUE}\"${AWS_ARN}\"${NC} (double-check this; perhaps needs to be a role within target account) \n"
    printf "caller_aws_arns = [\n  # include your own here if desired for testing\n]\n" >> $TFVARS_FILE
    printf "caller_gcp_service_account_ids = [\n " >> $TFVARS_FILE
    printf "  # put value here from Worklytics ( https://intl.worklytics.co/analytics/integrations/configuration )\n" >> $TFVARS_FILE
    printf "  # \"123456712345671234567\" # 21-digits, get this from Worklytics once prod-ready\n]\n" >> $TFVARS_FILE
  else
    printf "${RED}AWS CLI not available${NC}\n"
  fi
else
  printf "No AWS provider found in top-level of Terraform configuration. AWS CLI not required.\n"
fi

GOOGLE_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/google" | wc -l)
if test $GOOGLE_PROVIDER_COUNT -ne 0; then
  printf "Google provider in Terraform configuration. Initializing variables it requires ...\n"
  if gcloud --version &> /dev/null ; then
    GCP_PROJECT_ID=$(gcloud config get project)
    echo "gcp_project_id=\"${GCP_PROJECT_ID}\"" >> $TFVARS_FILE
    printf "\tgcp_project_id=${BLUE}\"${GCP_PROJECT_ID}\"${NC}\n"
    GOOGLE_WORKSPACE_EXAMPLE_USER=$(gcloud config get account)
    printf "google_workspace_example_user=\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"\n" >> $TFVARS_FILE
    printf "\tgoogle_workspace_example_user=${BLUE}\"${GOOGLE_WORKSPACE_EXAMPLE_USER}\"${NC}\n"
  else
    printf "${RED}gcloud not available${NC}\n"
  fi
else
  printf "No Google provider found in top-level of Terraform configuration. No gcloud initialization required.\n"
fi

AZUREAD_PROVIDER_COUNT=$(terraform providers | grep "${TOP_LEVEL_PROVIDER_PATTERN}/azuread" | wc -l)
if test $AZUREAD_PROVIDER_COUNT -ne 0; then
  printf "AzureAD provider in Terraform configuration. Initializing variables it requires ...\n"
  if az --version &> /dev/null ; then
    MSFT_TENANT_ID=$(az account show --query tenantId --output tsv)
    echo "msft_tenant_id=\"${MSFT_TENANT_ID}\"" >> $TFVARS_FILE
    printf "\tmsft_tenant_id=${BLUE}\"${MSFT_TENANT_ID}\"${NC}\n"
    MSFT_USER_EMAIL=$(az account show --query user.name --output tsv)
    printf "msft_owners_email=[\n  \"${MSFT_USER_EMAIL}\"\n]\n" >> $TFVARS_FILE
    printf "\tmsft_owners_email=${BLUE}[ \"${MSFT_USER_EMAIL}\" ]${NC}\n"
  else
    printf "${RED}az not available${NC}\n"
  fi
else
  printf "No Azure provider found in top-level of Terraform configuration. No Azure CLI initialization needed.\n"
fi


# give user some feedback
printf "Initialized example terraform vars file. Please open ${BLUE}${TFVARS_FILE}${NC} and customize it to your needs.\n"
printf "Review ${BLUE}variables.tf${NC} for descriptions of each variable.\n\n"
