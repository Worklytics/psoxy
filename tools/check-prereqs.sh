#!/bin/bash

# script to check Prereqs for proxy

printf "Checking that your machine has prerequisites for building/deploying Psoxy\n"
printf "See https://github.com/Worklytics/psoxy#prerequisites for more information ...\n\n"

HOMEBREW_AVAILABLE=`brew -v &> /dev/null`

RED='\e[0;31m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

if ! git --version &> /dev/null ; then
  printf "${RED}Git not installed.${NC} Not entirely sure how you got here without it, but to install see https://git-scm.com/book/en/v2/Getting-Started-Installing-Git\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install git${NC}\n"; fi
  exit 1
fi

if ! terraform -v &> /dev/null ; then
  printf "${RED}Terraform CLI not available.${NC} Psoxy examples / deployment scripts require it. See ${BLUE}https://developer.hashicorp.com/terraform/downloads${NC} for installation options\n"
  exit 1
fi

# Check Maven installation

if ! mvn -v &> /dev/null ; then
  printf "${RED}Maven not installed.${NC} See https://maven.apache.org/install.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install maven${NC}\n"; fi
  exit 1
fi


MVN_VERSION=`mvn -v | grep "Apache Maven"`
MVN_VERSION_MAJOR_MINOR=$(echo $MVN_VERSION | sed -n 's/^Apache Maven \([0-9]*\.[0-9]*\).*$/\1/p')
printf "Your Maven version is ${BLUE}${MVN_VERSION}${NC}.\n"

if (( $(echo "$MVN_VERSION_MAJOR_MINOR < 3.6" | bc ) == 1 )); then
  printf "${RED}This Maven version appears to be unsupported.${NC} Psoxy requires a supported version of Maven 3.6 or later.\n"
  printf "We recommend you upgrade. See https://maven.apache.org/install.html\n"
  printf "Maven is used to build the package that will be deployed to your host platform as an AWS lambda or a GCP Cloud Function\n"
fi

printf "\n"

# Check Java installation

JAVA_VERSION=`mvn -v | grep Java`
JAVA_VERSION_MAJOR=$(echo $JAVA_VERSION | sed -n 's/^Java version: \([0-9]*\).*/\1/p')

printf "Your Maven installation uses ${BLUE}${JAVA_VERSION}${NC}.\n"

if [[  "$JAVA_VERSION_MAJOR" != 17 && "$JAVA_VERSION_MAJOR" != 21  && "$JAVA_VERSION_MAJOR" != 23  ]]; then
  printf "${RED}This Java version appears to be unsupported. You should upgrade it, or may have compile errors.${NC} Psoxy requires an Oracle-supported version of Java 17 or later;  as of April 2025, this includes Java 17 or 21. See https://maven.apache.org/install.html\n"
  if $HOMEBREW_AVAILABLE; then printf "or as you have Homebrew available, run ${BLUE}brew install openjdk@17${NC}\n"; fi
  printf "If you have an alternative JDK installed, then you must update your ${BLUE}JAVA_HOME${NC} environment variable to point to it.\n"
fi

printf "\n"

# Check NPM installation

if ! npm -v &> /dev/null ; then
  printf "${RED}NodeJS (node) and Node Package Manager (npm) are not installed but are required for the local test tooling to work. ${NC} While this is optional, we recommend you install them to be able to test your instances. See https://nodejs.org/\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install node${NC}\n"; fi
fi


# Check AWS installation
AWSCLI_REASON="Required if deploying to AWS."
if ! aws --version &> /dev/null ; then
  printf "${RED}AWS CLI is not installed.${NC} ${AWSCLI_REASON} See https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install awscli${NC}\n"; fi
else
  printf "AWS CLI version ${BLUE}`aws --version`${NC} is installed.\n"
  printf ""
  printf "\t- make sure ${BLUE}aws sts get-caller-identity${NC} returns the user/role/account you expect. $AWSCLI_REASON\n"
fi

printf "\n"

# Check GCloud CLI installation
GCLOUD_REASON="Required if deploying to GCP or using Google Workspace data sources."
if ! gcloud --version &> /dev/null ; then
  printf "${RED}Google Cloud SDK is not installed.${NC} ${GCLOUD_REASON} See https://cloud.google.com/sdk/docs/install\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install --cask google-cloud-sdk${NC}\n"; fi
else
  printf "Google Cloud SDK version ${BLUE}`gcloud --version 2> /dev/null | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${BLUE}gcloud auth list --filter=\"status:ACTIVE\"${NC} returns the account you expect. $GCLOUD_REASON\n"
fi

printf "\n"

# Check Azure CLI installation
AZCLI_REASON="Required if deploying to Azure or using Microsoft 365 data sources."
if ! az --version &> /dev/null ; then
  printf "${RED}Azure CLI is not installed.${NC} ${AZCLI_REASON} See https://docs.microsoft.com/en-us/cli/azure/install-azure-cli\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install azure-cli${NC}\n"; fi
else
  # how can pipe to sed or something to strip extra whitespace out?
  printf "Azure CLI version ${BLUE}`az --version --only-show-errors | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${BLUE}az account show${NC} is the user/tenant you expect. If not, ${BLUE}az login --allow-no-subscription${NC} to authenticate. $AZCLI_REASON\n"
fi

