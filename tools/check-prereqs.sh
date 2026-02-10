#!/bin/bash

# script to check Prereqs for proxy

printf "Checking that your machine has prerequisites for building/deploying Psoxy\n"
printf "See https://github.com/Worklytics/psoxy#prerequisites for more information ...\n\n"

HOMEBREW_AVAILABLE=`brew -v &> /dev/null`

# Source centralized color scheme
source "$(dirname "$0")/set-term-colorscheme.sh"

if ! git --version &> /dev/null ; then
  printf "${ERROR}Git not installed.${NC} Not entirely sure how you got here without it, but to install see https://git-scm.com/book/en/v2/Getting-Started-Installing-Git\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install git${NC}\n"; fi
  exit 1
fi

if ! terraform -v &> /dev/null ; then
  printf "${ERROR}Terraform CLI not available.${NC} Psoxy examples / deployment scripts require it. See ${CODE}https://developer.hashicorp.com/terraform/downloads${NC} for installation options\n"
  exit 1
fi

# Check Maven installation

if ! mvn -v &> /dev/null ; then
  printf "${WARNING}Maven not installed.${NC} It is REQUIRED unless you will use a pre-built JAR. To install, see https://maven.apache.org/install.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install maven${NC}\n"; fi
  printf " (Using a prebuilt jar requires adding ${CODE}deployment_bundle=""${NC} to your ${CODE}terraform.tfvars${NC} file, and filling with s3/gcs uri for your desired JAR)\n"
else
  MVN_VERSION=`mvn -v | grep "Apache Maven"`
  MVN_VERSION_MAJOR_MINOR=$(echo $MVN_VERSION | sed -n 's/^Apache Maven \([0-9]*\.[0-9]*\).*$/\1/p')
  printf "Your Maven version is ${CODE}${MVN_VERSION}${NC}.\n"

  # Parse Maven version components
  MVN_MAJOR=$(echo "$MVN_VERSION_MAJOR_MINOR" | cut -d. -f1)
  MVN_MINOR=$(echo "$MVN_VERSION_MAJOR_MINOR" | cut -d. -f2)
  if (( MVN_MAJOR < 3 || (MVN_MAJOR == 3 && MVN_MINOR < 6) )); then
    printf "${ERROR}This Maven version appears to be unsupported.${NC} Psoxy requires a supported version of Maven 3.6 or later.\n"
    printf "We recommend you upgrade. See https://maven.apache.org/install.html\n"
    printf "Maven is used to build the package that will be deployed to your host platform as an AWS lambda or a GCP Cloud Function\n"
  fi

  printf "\n"

  # Check Java installation
  JAVA_VERSION=`mvn -v | grep Java`
  JAVA_VERSION_MAJOR=$(echo $JAVA_VERSION | sed -n 's/^Java version: \([0-9]*\).*/\1/p')

  printf "Your Maven installation uses ${CODE}${JAVA_VERSION}${NC}.\n"

  if [[  "$JAVA_VERSION_MAJOR" != 17 && "$JAVA_VERSION_MAJOR" != 21  && "$JAVA_VERSION_MAJOR" != 23  && "$JAVA_VERSION_MAJOR" != 24 ]]; then
    printf "${ERROR}This Java version appears to be unsupported. You should upgrade it, or may have compile errors.${NC} Psoxy requires an Oracle-supported version of Java 17 or later;  as of April 2025, this includes Java 17, 21, or 24. See https://maven.apache.org/install.html\n"
    if $HOMEBREW_AVAILABLE; then printf "or as you have Homebrew available, run ${CODE}brew install openjdk@17${NC}\n"; fi
    printf "If you have an alternative JDK installed, then you must update your ${CODE}JAVA_HOME${NC} environment variable to point to it.\n"
  fi

  printf "\n"

  # if java > 23, then mvn must be 3.9.10+
  if (( JAVA_VERSION_MAJOR > 23 )); then
    # Parse full Maven version for patch comparison
    MVN_VERSION_FULL=$(echo "$MVN_VERSION" | sed -n 's/^Apache Maven \([0-9]*\.[0-9]*\.[0-9]*\).*$/\1/p')
    MVN_PATCH=$(echo "$MVN_VERSION_FULL" | cut -d. -f3)
    if (( MVN_MAJOR < 3 || (MVN_MAJOR == 3 && MVN_MINOR < 9) || (MVN_MAJOR == 3 && MVN_MINOR == 9 && MVN_PATCH < 10) )); then
      printf "${ERROR}Maven < 3.9.10 has compatibility issues with Java 24.${NC} If you're using Java 24, psoxy will NOT build correctly unless you upgrade Maven to 3.9.10 or later.\n"
      printf "See https://maven.apache.org/install.html\n"
    fi
  fi

  printf "\n"
fi


# Check NPM installation

if ! npm -v &> /dev/null ; then
  printf "${WARNING}NodeJS (node) and Node Package Manager (npm) are not installed but are required for the local test tooling to work. ${NC} While this is optional, we recommend you install them to be able to test your instances. See https://nodejs.org/\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install node${NC}\n"; fi
fi


# Check AWS installation
AWSCLI_REASON="Required if deploying to AWS."
if ! aws --version &> /dev/null ; then
  printf "${ERROR}AWS CLI is not installed.${NC} ${AWSCLI_REASON} See https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install awscli${NC}\n"; fi
else
  printf "AWS CLI version ${CODE}`aws --version`${NC} is installed.\n"
  printf ""
  printf "\t- make sure ${CODE}aws sts get-caller-identity${NC} returns the user/role/account you expect. $AWSCLI_REASON\n"
fi

printf "\n"

# Check GCloud CLI installation
GCLOUD_REASON="Required if deploying to GCP or using Google Workspace data sources."
if ! gcloud --version &> /dev/null ; then
  printf "${ERROR}Google Cloud SDK is not installed.${NC} ${GCLOUD_REASON} See https://cloud.google.com/sdk/docs/install\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install --cask google-cloud-sdk${NC}\n"; fi
else
  printf "Google Cloud SDK version ${CODE}`gcloud --version 2> /dev/null | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${CODE}gcloud auth list --filter=\"status:ACTIVE\"${NC} returns the account you expect. $GCLOUD_REASON\n"
fi

printf "\n"

# Check Azure CLI installation
AZCLI_REASON="Required if deploying to Azure or using Microsoft 365 data sources."
if ! az --version &> /dev/null ; then
  printf "${ERROR}Azure CLI is not installed.${NC} ${AZCLI_REASON} See https://docs.microsoft.com/en-us/cli/azure/install-azure-cli\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${CODE}brew install azure-cli${NC}\n"; fi
else
  # how can pipe to sed or something to strip extra whitespace out?
  printf "Azure CLI version ${CODE}`az --version --only-show-errors | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${CODE}az account show${NC} is the user/tenant you expect. If not, ${CODE}az login --allow-no-subscription${NC} to authenticate. $AZCLI_REASON\n"
fi

