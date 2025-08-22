#!/bin/bash

# Publish Psoxy AWS JAR to multiple S3 buckets across regions
# Usage: ./publish-aws-release-artifact.sh [version]
# If version not provided, reads from java/pom.xml

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
IMPLEMENTATION="aws"
JAVA_SOURCE_ROOT="java/"
ROLE_ARN="arn:aws:iam::908404960471:role/InfraAdmin"
ROLE_SESSION_NAME="psoxy-artifact-publish-$(date +%s)"
BUCKET_PREFIX="psoxy-public-artifacts"

# AWS regions to publish to
REGIONS=(
    "us-east-1"
    "us-east-2"
    "us-west-1"
    "us-west-2"
)

# ensure current directory is the project root
if [ ! -f "java/pom.xml" ]; then
    echo -e "${RED}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
    exit 1
fi


# Get version from argument or pom.xml
if [ -n "$1" ]; then
    VERSION="$1"
else
    # Read version from pom.xml
    if [ ! -f "java/pom.xml" ]; then
        echo -e "${RED}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
        exit 1
    fi
    VERSION=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "java/pom.xml")
    if [ -z "$VERSION" ]; then
        echo -e "${RED}Error: Could not extract version from java/pom.xml${NC}"
        exit 1
    fi
fi

echo -e "${BLUE}=== Psoxy AWS Artifact Publisher ===${NC}"
echo -e "${BLUE}Version: ${GREEN}${VERSION}${NC}"
echo -e "${BLUE}Regions: ${GREEN}${REGIONS[*]}${NC}"
echo ""

# Check prerequisites
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    echo -e "${YELLOW}Install AWS CLI from: https://aws.amazon.com/cli/${NC}"
    exit 1
fi

# Check AWS CLI version
AWS_VERSION=$(aws --version 2>/dev/null | cut -d' ' -f1 | cut -d'/' -f2)
if [ -z "$AWS_VERSION" ]; then
    echo -e "${RED}Error: AWS CLI is installed but not working properly${NC}"
    exit 1
fi
echo -e "${BLUE}AWS CLI version: ${GREEN}${AWS_VERSION}${NC}"

# Check if AWS CLI is configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not configured/authenticated${NC}"
    echo -e "${YELLOW}Run 'aws configure' to set up your credentials${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is not installed${NC}"
    echo -e "Install with ${YELLOW}brew install jq${NC} or similar"
    exit 1
fi

# Show current AWS identity
CURRENT_IDENTITY=$(aws sts get-caller-identity --query 'Arn' --output text 2>/dev/null)
if [ $? -eq 0 ]; then
    echo -e "${BLUE}Current AWS identity: ${GREEN}${CURRENT_IDENTITY}${NC}"
fi


# run build with distribution profile
./tools/build.sh -d "$IMPLEMENTATION" "$JAVA_SOURCE_ROOT"
DEPLOYMENT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

# Validate JAR exists
JAR_PATH="${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment/${DEPLOYMENT_ARTIFACT}"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${RED}Error: JAR file not found at ${JAR_PATH} after running build script${NC}"
    echo -e "${YELLOW}Check last-build.log for errors${NC}"
    exit 1
fi

echo -e "${BLUE}Publishing Psoxy $IMPLEMENTATION JAR version ${GREEN}${VERSION}${BLUE} to S3 buckets...${NC}"
echo -e "${BLUE}JAR file: ${GREEN}${JAR_PATH}${NC}"
echo -e "${BLUE}Role: ${GREEN}${ROLE_ARN}${NC}"
echo ""

# Function to assume role and get temporary credentials
assume_role() {
    echo -e "${BLUE}Assuming role ${GREEN}${ROLE_ARN}${NC}..."

    # Assume the role and get temporary credentials
    CREDENTIALS=$(aws sts assume-role \
        --role-arn "$ROLE_ARN" \
        --role-session-name "$ROLE_SESSION_NAME" \
        --output json)

    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Failed to assume role${NC}"
        exit 1
    fi

    # Extract credentials
    export AWS_ACCESS_KEY_ID=$(echo "$CREDENTIALS" | jq -r '.Credentials.AccessKeyId')
    export AWS_SECRET_ACCESS_KEY=$(echo "$CREDENTIALS" | jq -r '.Credentials.SecretAccessKey')
    export AWS_SESSION_TOKEN=$(echo "$CREDENTIALS" | jq -r '.Credentials.SessionToken')

    echo -e "${GREEN}Successfully assumed role${NC}"
    echo ""
}

# Function to publish to a specific region
publish_to_region() {
    local region="$1"
    local bucket_name="${BUCKET_PREFIX}-${region}"
    local s3_path="s3://${bucket_name}/aws/${JAR_FILENAME}"

    echo -e "${BLUE}Publishing to ${GREEN}${region}${BLUE} (${s3_path})${NC}"

    # Check if bucket exists
    if ! aws s3 ls "s3://${bucket_name}" >/dev/null 2>&1; then
        echo -e "${YELLOW}Warning: Bucket ${bucket_name} does not exist in ${region}${NC}"
        echo -e "${YELLOW}Skipping ${region}${NC}"
        return 1
    fi

    # Upload with metadata
    aws s3 cp "$JAR_PATH" "$s3_path" \
        --region "$region" \
        --metadata "version=${VERSION},build-date=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        --cache-control "public, max-age=3600"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully published to ${region}${NC}"

        # Make the object publicly readable
        aws s3api put-object-acl \
            --bucket "$bucket_name" \
            --key "aws/${JAR_FILENAME}" \
            --acl public-read \
            --region "$region"

        echo -e "${GREEN}✓ Made object publicly readable in ${region}${NC}"
    else
        echo -e "${RED}✗ Failed to publish to ${region}${NC}"
        return 1
    fi

    echo ""
}

# Main execution
publish() {

    # Assume role
    assume_role

    # Publish to each region
    local success_count=0
    local total_regions=${#REGIONS[@]}

    for region in "${REGIONS[@]}"; do
        if publish_to_region "$region"; then
            ((success_count++))
        fi
    done

    echo -e "${BLUE}=== Summary ===${NC}"
    echo -e "${BLUE}Successfully published to ${GREEN}${success_count}/${total_regions}${BLUE} regions${NC}"

    if [ $success_count -eq $total_regions ]; then
        echo -e "${GREEN}✓ All regions published successfully!${NC}"
        echo ""
        echo -e "${BLUE}Download URLs:${NC}"
        for region in "${REGIONS[@]}"; do
            local bucket_name="${BUCKET_PREFIX}-${region}"
            echo -e "  ${GREEN}${region}:${NC} https://${bucket_name}.s3.${region}.amazonaws.com/aws/${JAR_FILENAME}"
        done
    else
        echo -e "${YELLOW}⚠ Some regions failed to publish${NC}"
        exit 1
    fi
}

# Run main publish function
publish "$@"
