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

if ! mvn -v &> /dev/null ; then
  printf "${RED}Maven not installed.${NC} See https://maven.apache.org/install.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install maven${NC}\n"; fi
  exit 1
fi

MVN_VERSION=`mvn -v | grep "Apache Maven"`

printf "Your Maven version is ${BLUE}${MVN_VERSION}${NC}.\n"
printf "\t- if that is a version < 3.6, we recommend you upgrade. See https://maven.apache.org/install.html\n"
printf "\t- Maven is used to build the package that will be deployed to your host platform as an AWS lambda or a GCP Cloud Function\n"

printf "\n"

JAVA_VERSION=`mvn -v | grep Java`

printf "Your Maven installation uses ${BLUE}${JAVA_VERSION}${NC}.\n"
printf "\t- if that is a Java version < 11, you must upgrade to 11. Java >= 11, <= 20 are supported.\n"
printf "\t- if you have a Java JDK of the right version installed on your machine *other* than the one referenced there, set your ${BLUE}JAVA_HOME${NC} to its location.\n"

printf "\n"

if ! npm -v &> /dev/null ; then
  printf "${RED}Node Package Manager (npm) is not installed and is required for some testing to work.${NC} See https://nodejs.org/\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install node${NC}\n"; fi
fi

AWSCLI_REASON="It is used if you're deploying to AWS."
if ! aws --version &> /dev/null ; then
  printf "${RED}AWS CLI is not installed.${NC} ${AWSCLI_REASON} See https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install awscli${NC}\n"; fi
else
  printf "AWS CLI version ${BLUE}`aws --version`${NC} is installed.\n"
  printf ""
  printf "\t- make sure ${BLUE}aws sts get-caller-identity${NC} returns the user/role/account you expect. $AWSCLI_REASON\n"
fi

printf "\n"

GCLOUD_REASON="It is used if you're deploying to GCP or using Google Workspace data sources."
if ! gcloud --version &> /dev/null ; then
  printf "${RED}Google Cloud SDK is not installed.${NC} ${GCLOUD_REASON} See https://cloud.google.com/sdk/docs/install\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install --cask google-cloud-sdk${NC}\n"; fi
else
  printf "Google Cloud SDK version ${BLUE}`gcloud --version 2> /dev/null | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${BLUE}gcloud auth list --filter=\"status:ACTIVE\"${NC} returns the account you expect. $GCLOUD_REASON\n"
fi

printf "\n"

AZCLI_REASON="It is used if you're deploying to Azure or using Microsoft 365 data sources."
if ! az --version &> /dev/null ; then
  printf "${RED}Azure CLI is not installed.${NC} ${AZCLI_REASON} See https://docs.microsoft.com/en-us/cli/azure/install-azure-cli\n"
  if $HOMEBREW_AVAILABLE; then printf " or, as you have Homebrew available, run ${BLUE}brew install azure-cli${NC}\n"; fi
else
  # how can pipe to sed or something to strip extra whitespace out?
  printf "Azure CLI version ${BLUE}`az --version --only-show-errors | head -n 1`${NC} is installed.\n"
  printf "\t- make sure ${BLUE}az account show${NC} is the user/tenant you expect. If not, ${BLUE}az login --allow-no-subscription${NC} to authenticate. $AZCLI_REASON\n"
fi
