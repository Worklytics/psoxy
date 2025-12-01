#!/bin/bash

# usage:
# ./tools/upgrade-tf-modules.sh <next-release> <path-to-clone>
# ./tools/upgrade-tf-modules.sh v0.4.30 ~/code/psoxy/
#
# NOTE: this is called 'upgrade', but in principal it can be used to downgrade as well. But in
# general proxy terraform modules aren't built to support downgrading, so YMMV

NEXT_RELEASE=$1

# colors
RED='\e[0;31m'
BLUE='\e[0;34m'
GREEN='\e[0;32m'
NC='\e[0m' # No Color

CURRENT_RELEASE=$(sed -n '/git::https:\/\/github\.com\/worklytics\/psoxy\//{s/.*ref=\([^"&]*\).*/\1/p;q;}' main.tf)

# if $NEXT_RELEASE is not provided, warn user and exit
if [ -z "$NEXT_RELEASE" ]; then
  printf "${RED}Next release version not specified. Exiting.${NC}\n"
  exit 1
fi

printf "Parsed your current terraform module version as ${BLUE}${CURRENT_RELEASE}${NC}; this script will upgrade it to ${GREEN}${NEXT_RELEASE}${NC}?\n"

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
    printf "${RED}Invalid input${NC}\n"
    exit 1
    ;;
esac


CURRENT_RELEASE_PATTERN=$(echo $CURRENT_RELEASE | sed 's/\./\\\./g')
PATTERN="s|ref=${CURRENT_RELEASE_PATTERN}|ref=${NEXT_RELEASE}|"

# find + sed to replace all references to the current release with the next release
find . -type f -name "*.tf" -exec sed -i.bck "${PATTERN}" {} +

# delete the sed backup files
find . -type f -name "*.bck" -delete

terraform init

printf "Terraform module versions upgraded to ${GREEN}${NEXT_RELEASE}${NC}.\n"
printf "To revert: ${BLUE}$0 ${CURRENT_RELEASE}${NC}\n"

if grep -q '^[[:space:]]*deployment_bundle[[:space:]]*=' terraform.tfvars; then
    # Extract current deployment_bundle value
    CURRENT_BUNDLE=$(grep '^[[:space:]]*deployment_bundle[[:space:]]*=' terraform.tfvars | sed 's/.*=[[:space:]]*"\([^"]*\)".*/\1/' | head -1)
    
    if [ -n "$CURRENT_BUNDLE" ]; then
        printf "\nYour ${BLUE}terraform.tfvars${NC} file references a pre-built 'deployment_bundle':\n"
        printf "  ${BLUE}${CURRENT_BUNDLE}${NC}\n\n"
        
        # Check if this is a public bundle (in expected public bucket locations)
        IS_PUBLIC_BUNDLE=false
        PLATFORM=""
        
        # Check for AWS public bundle: s3://psoxy-public-artifacts-{region}/psoxy-aws-{version}.jar
        if [[ "$CURRENT_BUNDLE" =~ ^s3://psoxy-public-artifacts- ]]; then
            IS_PUBLIC_BUNDLE=true
            PLATFORM="aws"
        # Check for GCP public bundle: gs://psoxy-public-artifacts/psoxy-gcp-{version}.zip
        elif [[ "$CURRENT_BUNDLE" =~ ^gs://psoxy-public-artifacts/psoxy-gcp- ]]; then
            IS_PUBLIC_BUNDLE=true
            PLATFORM="gcp"
        fi
        
        if [ "$IS_PUBLIC_BUNDLE" = true ]; then
            # Case 1: Public bundle - check for new version and offer to upgrade
            # Extract version number from NEXT_RELEASE (remove 'v' prefix and 'rc-' prefix if present)
            NEXT_VERSION=$(echo "$NEXT_RELEASE" | sed 's/^rc-//' | sed 's/^v//')
            
            BUNDLE_UPDATED=false
            
            if [ "$PLATFORM" = "aws" ]; then
                if command -v aws &> /dev/null; then
                    # Extract region from current bundle path or get from terraform.tfvars/AWS config
                    AWS_REGION=""
                    if [[ "$CURRENT_BUNDLE" =~ s3://psoxy-public-artifacts-([^/]+)/ ]]; then
                        AWS_REGION="${BASH_REMATCH[1]}"
                    fi
                    if [ -z "$AWS_REGION" ]; then
                        if grep -q '^[[:space:]]*aws_region[[:space:]]*=' terraform.tfvars 2>/dev/null; then
                            AWS_REGION=$(grep '^[[:space:]]*aws_region[[:space:]]*=' terraform.tfvars | sed 's/.*=[[:space:]]*"\([^"]*\)".*/\1/' | head -1)
                        fi
                    fi
                    if [ -z "$AWS_REGION" ]; then
                        AWS_REGION=$(aws configure get region 2>/dev/null || echo "")
                    fi
                    if [ -z "$AWS_REGION" ]; then
                        AWS_REGION="us-east-1"
                    fi
                    
                    BUCKET_NAME="psoxy-public-artifacts-${AWS_REGION}"
                    NEW_BUNDLE_PATH="s3://${BUCKET_NAME}/psoxy-aws-${NEXT_VERSION}.jar"
                    
                    # Check if new bundle exists
                    if aws s3 ls "$NEW_BUNDLE_PATH" >/dev/null 2>&1; then
                        # Update bundle reference
                        sed -i.bck "s|^\([[:space:]]*deployment_bundle[[:space:]]*=\).*|\1 \"${NEW_BUNDLE_PATH}\"|" terraform.tfvars
                        rm -f terraform.tfvars.bck 2>/dev/null
                        printf "Updated ${BLUE}deployment_bundle${NC} to:\n"
                        printf "  ${GREEN}${NEW_BUNDLE_PATH}${NC}\n\n"
                        BUNDLE_UPDATED=true
                    else
                        printf "${BLUE}Warning:${NC} Bundle for version ${GREEN}${NEXT_VERSION}${NC} not found at ${BLUE}${NEW_BUNDLE_PATH}${NC}\n"
                        printf "Bundle reference was not updated. You may need to build it locally or wait for it to be published.\n\n"
                    fi
                else
                    printf "${BLUE}Note:${NC} AWS CLI not available. Cannot check for updated bundle in public S3 bucket.\n\n"
                fi
            elif [ "$PLATFORM" = "gcp" ]; then
                if command -v gsutil &> /dev/null; then
                    BUCKET_NAME="psoxy-public-artifacts"
                    NEW_BUNDLE_PATH="gs://${BUCKET_NAME}/psoxy-gcp-${NEXT_VERSION}.zip"
                    
                    # Check if new bundle exists
                    if gsutil ls "$NEW_BUNDLE_PATH" >/dev/null 2>&1; then
                        # Update bundle reference
                        sed -i.bck "s|^\([[:space:]]*deployment_bundle[[:space:]]*=\).*|\1 \"${NEW_BUNDLE_PATH}\"|" terraform.tfvars
                        rm -f terraform.tfvars.bck 2>/dev/null
                        printf "Updated ${BLUE}deployment_bundle${NC} to:\n"
                        printf "  ${GREEN}${NEW_BUNDLE_PATH}${NC}\n\n"
                        BUNDLE_UPDATED=true
                    else
                        printf "${BLUE}Warning:${NC} Bundle for version ${GREEN}${NEXT_VERSION}${NC} not found at ${BLUE}${NEW_BUNDLE_PATH}${NC}\n"
                        printf "Bundle reference was not updated. You may need to build it locally or wait for it to be published.\n\n"
                    fi
                else
                    printf "${BLUE}Note:${NC} gsutil not available. Cannot check for updated bundle in public GCS bucket.\n\n"
                fi
            fi
            
            # Warn about compatibility
            if [ "$BUNDLE_UPDATED" = true ]; then
                printf "${BLUE}Note:${NC} Ensure that the AWS Lambda / GCP Cloud Function bundle you're using is compatible with the Terraform modules version ${GREEN}${NEXT_RELEASE}${NC} you're upgrading to.\n\n"
            else
                printf "${BLUE}Warning:${NC} You have an explicit bundle referenced. Ensure that the AWS Lambda / GCP Cloud Function bundle you're using is compatible with the Terraform modules version ${GREEN}${NEXT_RELEASE}${NC} you're upgrading to.\n"
                printf "If the bundle version doesn't match the module version, you may encounter compatibility issues.\n\n"
            fi
        else
            # Case 2: Custom bundle (not in public buckets) - offer to rebuild locally
            printf "This appears to be a custom bundle reference (not in public artifact buckets).\n"
            read -p "Do you want to run './update-bundle' to re-build it with a version matching the terraform modules you just updated? [Y/n] " response

            # If user presses Enter without input, default to 'Y'
            if [[ -z "$response" || "$response" =~ ^[Yy]$ ]]; then
                if [ -f "./update-bundle" ]; then
                    ./update-bundle
                else
                    printf "${RED}Error:${NC} ./update-bundle script not found. You may need to build the bundle manually.\n\n"
                fi
            else
                echo "Bundle not updated."
            fi
            
            printf "\n${BLUE}Warning:${NC} You have an explicit bundle referenced. Ensure that the AWS Lambda / GCP Cloud Function bundle you're using is compatible with the Terraform modules version ${GREEN}${NEXT_RELEASE}${NC} you're upgrading to.\n"
            printf "If the bundle version doesn't match the module version, you may encounter compatibility issues.\n\n"
        fi
    fi
fi


# parse NEXT_RELEASE as something of the form `rc-v0.5.6` or `v0.5.6`, as MAJOR.MINOR.PATCH
NEXT_MAJOR=$(echo $NEXT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\1/')
NEXT_MINOR=$(echo $NEXT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\2/')
NEXT_PATCH=$(echo $NEXT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\3/')

# parse CURRENT_RELEASE as something of the form `rc-v0.5.6` or `v0.5.6`, as MAJOR.MINOR.PATCH
CURRENT_MAJOR=$(echo $CURRENT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\1/')
CURRENT_MINOR=$(echo $CURRENT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\2/')
CURRENT_PATCH=$(echo $CURRENT_RELEASE | sed 's/^v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)$/\3/')

if [ $NEXT_MINOR -gt $CURRENT_MINOR ]; then
  printf "Next release version *may* include a provider bump. It is recommended to run ${BLUE} terraform init --upgrade${NC} to get the latest versions of all terraform providers that are compatible with your configuration.\n"
  printf "You may first wish to run ${BLUE}terraform providers${NC} to review the various provider version constraints, and consider revising them in top-level ${BLUE}main.tf${NC} or wherever they're specified.\n"
fi