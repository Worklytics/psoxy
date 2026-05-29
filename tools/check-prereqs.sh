#!/bin/bash

# script to check Prereqs for proxy

printf "Checking that your machine has prerequisites for building/deploying Psoxy\n"
printf "See https://github.com/Worklytics/psoxy#prerequisites for more information ...\n\n"

HOMEBREW_AVAILABLE=`brew -v &> /dev/null`

CI_MODE=false
for arg in "$@"; do
  if [[ "$arg" == "--ci" ]] || [[ "$arg" == "--non-interactive" ]]; then
    CI_MODE=true
  fi
done

COLORSCHEME_SH="$(dirname "$0")/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

if ! git --version &> /dev/null ; then
  printf "${ERR}Git not installed.${NC} Not entirely sure how you got here without it, but to install see https://git-scm.com/book/en/v2/Getting-Started-Installing-Git\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install git${NC}\n"; fi
  if [[ "$CI_MODE" != "true" ]]; then
    exit 1
  fi
fi

if ! terraform -v &> /dev/null ; then
  printf "${ERR}Terraform CLI not available.${NC} Psoxy examples / deployment scripts require it. See ${CODE}https://developer.hashicorp.com/terraform/downloads${NC} for installation options\n"
  if [[ "$CI_MODE" != "true" ]]; then
    exit 1
  fi
else
  TF_VERSION_FULL=$(terraform -version | head -n 1)
  TF_VERSION_MAJOR_MINOR=$(echo "$TF_VERSION_FULL" | sed -n 's/^Terraform v\([0-9]*\.[0-9]*\).*$/\1/p')
  TF_MAJOR=$(echo "$TF_VERSION_MAJOR_MINOR" | cut -d. -f1)
  TF_MINOR=$(echo "$TF_VERSION_MAJOR_MINOR" | cut -d. -f2)
  if (( TF_MAJOR < 1 || (TF_MAJOR == 1 && TF_MINOR < 7) )); then
    printf "${ERR}This Terraform version appears to be unsupported.${NC} Psoxy requires a supported version of Terraform 1.7 or later.\n"
    printf "We recommend you upgrade. See https://developer.hashicorp.com/terraform/downloads\n"
  else
    printf "Your Terraform version is ${CODE}${TF_VERSION_FULL}${NC}.\n"
  fi
fi

# Check Maven installation

if ! mvn -v &> /dev/null ; then
  printf "${WARN}Maven not installed.${NC} It is REQUIRED unless you will use a pre-built JAR.\n"
  printf " Note: Java JDK and Maven are only needed if building and bundling the java from source.\n"
  printf " To install Maven, see https://maven.apache.org/install.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install maven${NC}\n"; fi
  printf " (Using a prebuilt jar requires adding ${CODE}deployment_bundle=""${NC} to your ${CODE}terraform.tfvars${NC} file, and filling with s3/gcs uri for your desired JAR. The JRE of your host platform (AWS/GCP) will still be used at runtime).\n"
else
  MVN_VERSION=`mvn -v | grep "Apache Maven"`
  MVN_VERSION_MAJOR_MINOR=$(echo $MVN_VERSION | sed -n 's/^Apache Maven \([0-9]*\.[0-9]*\).*$/\1/p')
  printf "Your Maven version is ${CODE}${MVN_VERSION}${NC}.\n"

  # Parse Maven version components
  MVN_MAJOR=$(echo "$MVN_VERSION_MAJOR_MINOR" | cut -d. -f1)
  MVN_MINOR=$(echo "$MVN_VERSION_MAJOR_MINOR" | cut -d. -f2)
  if (( MVN_MAJOR < 3 || (MVN_MAJOR == 3 && MVN_MINOR < 6) )); then
    printf "${ERR}This Maven version appears to be unsupported.${NC} Psoxy requires a supported version of Maven 3.6 or later.\n"
    printf "We recommend you upgrade. See https://maven.apache.org/install.html\n"
    printf "Maven is used to build the package that will be deployed to your host platform as an AWS lambda or a GCP Cloud Function\n"
  fi

  printf "\n"

  # Check Java installation
  JAVA_VERSION=`mvn -v | grep Java`
  JAVA_VERSION_MAJOR=$(echo $JAVA_VERSION | sed -n 's/^Java version: \([0-9]*\).*/\1/p')

  printf "Your Maven installation uses ${CODE}${JAVA_VERSION}${NC}.\n"

  if [[  "$JAVA_VERSION_MAJOR" != 21  && "$JAVA_VERSION_MAJOR" != 25 && "$JAVA_VERSION_MAJOR" != 26 ]]; then
    printf "${ERR}This Java version appears to be unsupported. You should upgrade it, or may have compile errors.${NC} Psoxy requires an Oracle-supported version of Java 21 or later;  as of March 2026, this includes Java 21, 25, and 26. See https://maven.apache.org/install.html\n"
    if $HOMEBREW_AVAILABLE; then printf "or as you have Homebrew available, run ${CODE}brew install openjdk@21${NC}\n"; fi
    printf "If you have an alternative JDK installed, then you must update your ${CODE}JAVA_HOME${NC} environment variable to point to it.\n"
  fi

  printf "\n"

  # if java > 23, then mvn must be 3.9.10+
  if (( JAVA_VERSION_MAJOR > 23 )); then
    # Parse full Maven version for patch comparison
    MVN_VERSION_FULL=$(echo "$MVN_VERSION" | sed -n 's/^Apache Maven \([0-9]*\.[0-9]*\.[0-9]*\).*$/\1/p')
    MVN_PATCH=$(echo "$MVN_VERSION_FULL" | cut -d. -f3)
    if (( MVN_MAJOR < 3 || (MVN_MAJOR == 3 && MVN_MINOR < 9) || (MVN_MAJOR == 3 && MVN_MINOR == 9 && MVN_PATCH < 10) )); then
      printf "${ERR}Maven < 3.9.10 has compatibility issues with Java 24.${NC} If you're using Java 24, psoxy will NOT build correctly unless you upgrade Maven to 3.9.10 or later.\n"
      printf "See https://maven.apache.org/install.html\n"
    fi
  fi

  printf "\n"
fi

# Check NPM installation

if ! npm -v &> /dev/null ; then
  printf "${WARN}NodeJS (node) and Node Package Manager (npm) are not installed but are required for the local test tooling to work. ${NC} While this is optional, we recommend you install them to be able to test your instances. See https://nodejs.org/\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install node${NC}\n"; fi
fi

# Check AWS installation
AWSCLI_REASON="Required if deploying to AWS."
if ! aws --version &> /dev/null ; then
  printf "${ERR}AWS CLI is not installed.${NC} ${AWSCLI_REASON} See https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install awscli${NC}\n"; fi
else
  printf "AWS CLI version ${CODE}`aws --version`${NC} is installed.\n"
  printf ""
  printf "\t- make sure ${CODE}aws sts get-caller-identity${NC} returns the user/role/account you expect. $AWSCLI_REASON\n"

  if aws sts get-caller-identity &> /dev/null; then
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text 2>/dev/null)
    # the || true ensures that we fail silently even if set -e is on, and the 2>/dev/null handles standard error
    AWS_CONCURRENCY=$(aws lambda get-account-settings --query 'AccountLimit.ConcurrentExecutions' --output text 2>/dev/null || true)
    if [[ -n "$AWS_CONCURRENCY" && "$AWS_CONCURRENCY" =~ ^[0-9]+$ ]]; then
      if (( AWS_CONCURRENCY < 1000 )); then
        printf "\t- ${WARN}Warning: AWS Lambda account-level concurrency quota for account $AWS_ACCOUNT_ID is $AWS_CONCURRENCY, which is < 1000.${NC}\n"
        printf "\t  If this is the AWS account to which your lambda instances will be deployed, ensure that this amount is sufficient for your use case (we recommend at least 100).\n"
      else
        printf "\t- AWS Lambda account-level concurrency quota for account ${CODE}${AWS_ACCOUNT_ID}${NC} is ${CODE}${AWS_CONCURRENCY}${NC}.\n"
      fi
    fi

    # Check for IAM Role quotas
    AWS_IAM_ROLES_QUOTA=$(aws service-quotas get-service-quota --service-code iam --quota-code L-FE177D64 --query 'Quota.Value' --output text 2>/dev/null || true)
    if [[ -n "$AWS_IAM_ROLES_QUOTA" && "$AWS_IAM_ROLES_QUOTA" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
       AWS_IAM_ROLES_QUOTA=${AWS_IAM_ROLES_QUOTA%.*} # truncate decimals
       printf "\t- AWS IAM Roles quota for account ${CODE}${AWS_ACCOUNT_ID}${NC} is ${CODE}${AWS_IAM_ROLES_QUOTA}${NC}.\n"
       if (( AWS_IAM_ROLES_QUOTA < 1000 )); then
          printf "\t  ${WARN}Warning: you may need a higher limit if deploying many Psoxy instances.${NC}\n"
       fi
    fi
  fi
fi

printf "\n"

# Check GCloud CLI installation
GCLOUD_REASON="Required if deploying to GCP or using Google Workspace data sources."
if ! gcloud --version &> /dev/null ; then
  printf "${ERR}Google Cloud SDK is not installed.${NC} ${GCLOUD_REASON} See https://cloud.google.com/sdk/docs/install\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install --cask google-cloud-sdk${NC}\n"; fi
else
  printf "Google Cloud SDK version ${CODE}`gcloud --version 2> /dev/null | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${CODE}gcloud auth list --filter=\"status:ACTIVE\"${NC} returns the account you expect. $GCLOUD_REASON\n"

  if gcloud auth list --filter="status:ACTIVE" --format="value(account)" 2>/dev/null | grep -q '@'; then
    GCP_PROJECT_ID=$(gcloud config get-value project 2>/dev/null || true)
    if [[ -n "$GCP_PROJECT_ID" ]]; then
      # Check Cloud Functions Quota
      GCP_FUNCTIONS_QUOTA=$(gcloud compute project-info describe --project="$GCP_PROJECT_ID" --format="value(quotas.value)" --flatten="quotas[]" --filter="quotas.metric:CLOUD_FUNCTIONS_API_REQUESTS_PER_100_SECONDS" 2>/dev/null || true)
      if [[ -n "$GCP_FUNCTIONS_QUOTA" && "$GCP_FUNCTIONS_QUOTA" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
         GCP_FUNCTIONS_QUOTA=${GCP_FUNCTIONS_QUOTA%.*} # truncate decimals
         printf "\t- GCP Cloud Functions (per 100s) quota for project ${CODE}${GCP_PROJECT_ID}${NC} is ${CODE}${GCP_FUNCTIONS_QUOTA}${NC}.\n"
      fi
    fi
  fi
fi

printf "\n"

# Check Azure CLI installation
AZCLI_REASON="Required if deploying to Azure or using Microsoft 365 data sources."
AZ_ENTRA_ADMIN_ROLES_REASON="Microsoft 365 / Entra connectors require the authenticated principal to have Global Administrator or Application Administrator directory roles."
AZ_ENTRA_ROLES_DOC="https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/view-assignments"
if ! az --version &> /dev/null ; then
  printf "${ERR}Azure CLI is not installed.${NC} ${AZCLI_REASON} See https://docs.microsoft.com/en-us/cli/azure/install-azure-cli\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install azure-cli${NC}\n"; fi
  printf "\t- ${WARN}If you intend to use Microsoft 365 connectors, your Azure principal will still need ${CODE}Global Administrator${NC} or ${CODE}Application Administrator${NC} Entra directory roles. See ${AZ_ENTRA_ROLES_DOC}${NC}\n"
else
  # how can pipe to sed or something to strip extra whitespace out?
  printf "Azure CLI version ${CODE}`az --version --only-show-errors | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${CODE}az account show${NC} is the user/tenant you expect. If not, ${CODE}az login --allow-no-subscriptions${NC} to authenticate. $AZCLI_REASON\n"

  if az account show &> /dev/null; then
    AZ_TENANT_ID=$(az account show --query tenantId -o tsv 2>/dev/null)
    AZ_USER_NAME=$(az account show --query user.name -o tsv 2>/dev/null)
    if [[ -n "$AZ_USER_NAME" ]]; then
      printf "\t- signed in as ${CODE}${AZ_USER_NAME}${NC}"
      if [[ -n "$AZ_TENANT_ID" ]]; then
        printf " (tenant ${CODE}${AZ_TENANT_ID}${NC})"
      fi
      printf ".\n"
    fi

    AZ_GRAPH_ROLES_URL="https://graph.microsoft.com/v1.0/me/transitiveMemberOf/microsoft.graph.directoryRole"
    AZ_SKIP_ROLE_CHECK=false
    AZ_PRINCIPAL_TYPE=$(az account show --query user.type -o tsv 2>/dev/null)
    if [[ "$AZ_PRINCIPAL_TYPE" == "servicePrincipal" ]]; then
      AZ_SP_APP_ID=$(az account show --query user.name -o tsv 2>/dev/null)
      AZ_SP_OBJECT_ID=$(az ad sp show --id "$AZ_SP_APP_ID" --query id -o tsv 2>/dev/null)
      if [[ -z "$AZ_SP_OBJECT_ID" ]]; then
        AZ_SKIP_ROLE_CHECK=true
        printf "\t- ${WARN}Could not resolve service principal object ID for Entra directory role check.${NC}\n"
        printf "\t  ${AZ_ENTRA_ADMIN_ROLES_REASON} Verify assignments in the Microsoft Entra admin center. See ${AZ_ENTRA_ROLES_DOC}\n"
      else
        AZ_GRAPH_ROLES_URL="https://graph.microsoft.com/v1.0/servicePrincipals/${AZ_SP_OBJECT_ID}/transitiveMemberOf/microsoft.graph.directoryRole"
      fi
    fi

    if [[ "$AZ_SKIP_ROLE_CHECK" != "true" ]]; then
      AZ_ROLE_NAMES=$(az rest --method GET --url "$AZ_GRAPH_ROLES_URL" --query "value[].displayName" -o tsv 2>/dev/null)
      AZ_REST_EXIT=$?
      if [[ $AZ_REST_EXIT -ne 0 ]]; then
        printf "\t- ${WARN}Could not verify Entra directory roles (Microsoft Graph request failed).${NC}\n"
        printf "\t  ${AZ_ENTRA_ADMIN_ROLES_REASON} Verify assignments in the Microsoft Entra admin center. See ${AZ_ENTRA_ROLES_DOC}\n"
      else
        AZ_HAS_REQUIRED_ROLE=false
        while IFS= read -r AZ_ROLE_NAME; do
          if [[ "$AZ_ROLE_NAME" == "Global Administrator" || "$AZ_ROLE_NAME" == "Application Administrator" ]]; then
            AZ_HAS_REQUIRED_ROLE=true
            break
          fi
        done <<< "$AZ_ROLE_NAMES"

        if $AZ_HAS_REQUIRED_ROLE; then
          printf "\t- ${SUCCESS}Entra directory roles: signed-in principal has Global Administrator or Application Administrator.${NC}\n"
        else
          AZ_ROLE_LIST="${AZ_ROLE_NAMES//$'\n'/, }"
          if [[ -z "$AZ_ROLE_LIST" ]]; then
            AZ_ROLE_LIST="(none detected)"
          fi
          printf "\t- ${WARN}Entra directory roles: signed-in principal does not appear to have Global Administrator or Application Administrator.${NC}\n"
          printf "\t  ${AZ_ENTRA_ADMIN_ROLES_REASON}\n"
          printf "\t  Directory roles detected for this principal: ${CODE}${AZ_ROLE_LIST}${NC}.\n"
          printf "\t  See ${AZ_ENTRA_ROLES_DOC}\n"
        fi
      fi
    fi
  else
    printf "\t- ${WARN}Azure CLI is not authenticated.${NC} Run ${CODE}az login --allow-no-subscriptions${NC} to authenticate.\n"
    printf "\t  Once authenticated, re-run this script to verify Entra directory role requirements (${CODE}Global Administrator${NC} or ${CODE}Application Administrator${NC}).\n"
  fi
fi

