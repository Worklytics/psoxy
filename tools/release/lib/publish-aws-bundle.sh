#!/bin/bash

# Publish Psoxy AWS JAR to multiple S3 buckets across regions
# Usage: ./publish-aws-bundle.sh [--rc] [--non-interactive] 
#   --rc:              Mark this as a release candidate build (adds -rc suffix to artifact name)
#   --non-interactive: Skip all interactive prompts (auto-confirm all prompts)
#
# Examples:
#   ./publish-aws-bundle.sh                    
#   ./publish-aws-bundle.sh --rc               # RC build
#   ./publish-aws-bundle.sh --non-interactive  # Non-interactive mode (for CI)

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration (use env vars if set, otherwise defaults for local use)
IMPLEMENTATION="${IMPLEMENTATION:-aws}"
JAVA_SOURCE_ROOT="${JAVA_SOURCE_ROOT:-java/}"
ROLE_ARN="${ROLE_ARN:-arn:aws:iam::908404960471:role/InfraAdmin}"
ROLE_SESSION_NAME="${ROLE_SESSION_NAME:-psoxy-artifact-publish-$(date +%s)}"
BUCKET_PREFIX="${BUCKET_PREFIX:-psoxy-public-artifacts}"

# AWS regions to publish to (comma-separated string from env, or default array)
if [ -n "$AWS_REGIONS" ]; then
    # Convert comma-separated string to array
    IFS=',' read -ra REGIONS_ARRAY <<< "$AWS_REGIONS"
    REGIONS=("${REGIONS_ARRAY[@]}")
else
    # Default regions
    REGIONS=(
        "us-east-1"
        "us-east-2"
        "us-west-1"
        "us-west-2"
    )
fi

# ensure current directory is the project root
if [ ! -f "java/pom.xml" ]; then
    echo -e "${RED}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
    exit 1
fi

# Parse command-line arguments
IS_RC_BUILD=false
NON_INTERACTIVE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --rc)
            IS_RC_BUILD=true
            echo -e "${BLUE}RC build flag detected${NC}"
            shift
            ;;
        --non-interactive)
            NON_INTERACTIVE=true
            echo -e "${BLUE}Non-interactive mode enabled${NC}"
            shift
            ;;
        -*)
            echo -e "${RED}Error: Unknown option: $1${NC}"
            echo "Usage: $0 [--rc] [--non-interactive]"
            exit 1
            ;;
        *)
            # Treat as version argument
            VERSION="$1"
            shift
            ;;
    esac
done

# Get version from pom.xml
VERSION=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "java/pom.xml")
if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: Could not extract version from java/pom.xml${NC}"
    exit 1
fi

# Function to validate git branch/tag matches version requirements
validate_git_branch_or_tag() {
    # Check if git is available
    if ! command -v git &> /dev/null; then
        echo -e "${YELLOW}Warning: git is not installed, skipping git validation${NC}"
        return 0
    fi
    
    # Check if we're in a git repository
    if ! git rev-parse --git-dir >/dev/null 2>&1; then
        echo -e "${YELLOW}Warning: Not in a git repository, skipping git validation${NC}"
        return 0
    fi
    
    # Get current branch or tag
    local current_ref
    if git describe --exact-match --tags HEAD >/dev/null 2>&1; then
        # We're on a tag
        current_ref=$(git describe --exact-match --tags HEAD)
    else
        # We're on a branch
        current_ref=$(git rev-parse --abbrev-ref HEAD)
    fi
    
    # Expected tag format: v{VERSION}
    local expected_tag="v${VERSION}"
    
    # Check if we're on main branch or matching tag
    if [ "$current_ref" = "main" ]; then
        echo -e "${GREEN}✓ Running on main branch${NC}"
        return 0
    elif [ "$current_ref" = "$expected_tag" ]; then
        echo -e "${GREEN}✓ Running on tag ${expected_tag}${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ Warning: Not running on main branch or tag ${expected_tag}${NC}"
        echo -e "${YELLOW}Current git reference: ${current_ref}${NC}"
        echo -e "${YELLOW}Version: ${VERSION}${NC}"
        echo ""
        echo -e "${YELLOW}Recommended: Run from main branch or tag ${expected_tag}${NC}"
        
        if [ "$NON_INTERACTIVE" = "true" ]; then
            echo -e "${BLUE}Non-interactive mode: Auto-proceeding${NC}"
            return 0
        fi
        
        echo -e "${YELLOW}Do you want to proceed anyway? (yes/no):${NC} "
        read -r response
        
        case "$response" in
            [yY][eE][sS]|[yY])
                echo -e "${YELLOW}Proceeding with publish from ${current_ref}...${NC}"
                return 0
                ;;
            *)
                echo -e "${YELLOW}Publishing cancelled by user${NC}"
                exit 0
                ;;
        esac
    fi
}

# Validate git branch/tag before proceeding
validate_git_branch_or_tag
echo ""

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
BUILT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

# Validate JAR exists
JAR_PATH="${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment/${BUILT_ARTIFACT}"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${RED}Error: JAR file not found at ${JAR_PATH} after running build script${NC}"
    echo -e "${YELLOW}Check last-build.log for errors${NC}"
    exit 1
fi

# Construct deployment artifact name
# RC builds should have artifact name like: psoxy-aws-0.5.15-rc.jar
# Use explicit boolean check
if [ "$IS_RC_BUILD" = "true" ] || [ "$IS_RC_BUILD" = "1" ]; then
    # Add -rc before .jar extension
    DEPLOYMENT_ARTIFACT="${BUILT_ARTIFACT%.jar}-rc.jar"
else
    DEPLOYMENT_ARTIFACT="$BUILT_ARTIFACT"
fi

echo -e "${BLUE}Publishing Psoxy $IMPLEMENTATION JAR version ${GREEN}${VERSION}${BLUE} to S3 buckets...${NC}"
echo -e "${BLUE}JAR file: ${GREEN}${JAR_PATH}${NC}"
echo -e "${BLUE}Role: ${GREEN}${ROLE_ARN}${NC}"
echo ""

# Function to check if artifact already exists in any S3 bucket
# Outputs regions where artifacts exist, returns 0 if any exist, 1 if none exist
check_artifacts_exist() {
    local existing_regions=()
    
    for region in "${REGIONS[@]}"; do
        local bucket_name="${BUCKET_PREFIX}-${region}"
        local s3_path="s3://${bucket_name}/${DEPLOYMENT_ARTIFACT}"
        
        if aws s3 ls "$s3_path" >/dev/null 2>&1; then
            existing_regions+=("$region")
        fi
    done
    
    if [ ${#existing_regions[@]} -gt 0 ]; then
        printf '%s\n' "${existing_regions[@]}"
        return 0  # Artifacts exist
    else
        return 1  # No artifacts exist
    fi
}

# Function to prompt user for confirmation
prompt_overwrite() {
    local existing_regions=("$@")
    
    echo -e "${YELLOW}Warning: Artifact already exists in the following regions:${NC}"
    for region in "${existing_regions[@]}"; do
        local bucket_name="${BUCKET_PREFIX}-${region}"
        local s3_path="s3://${bucket_name}/${DEPLOYMENT_ARTIFACT}"
        echo -e "  ${BLUE}${region}:${NC} ${s3_path}"
    done
    echo ""
    
    if [ "$NON_INTERACTIVE" = "true" ]; then
        echo -e "${BLUE}Non-interactive mode: Auto-overwriting${NC}"
        return 0
    fi
    
    echo -e "${YELLOW}Do you want to overwrite these artifacts? (yes/no):${NC} "
    read -r response
    
    case "$response" in
        [yY][eE][sS]|[yY])
            return 0  # User confirmed
            ;;
        *)
            return 1  # User declined
            ;;
    esac
}

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
    local s3_path="s3://${bucket_name}/${DEPLOYMENT_ARTIFACT}"

    echo -e "${BLUE}Publishing to ${GREEN}${region}${BLUE} (${s3_path})${NC}"

    # Check if bucket exists
    if ! aws s3 ls "s3://${bucket_name}" >/dev/null 2>&1; then
        echo -e "${YELLOW}Warning: Bucket ${bucket_name} does not exist in ${region}${NC}"
        echo -e "${YELLOW}Skipping ${region}${NC}"
        return 1
    fi

    # Build metadata string
    local metadata="version=${VERSION},build-date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    
    # Add gh_ref metadata if available (from GitHub Actions)
    if [ -n "${GH_REF:-}" ]; then
        metadata="${metadata},gh_ref=${GH_REF}"
        echo -e "${BLUE}Adding metadata: gh_ref=${GH_REF}${NC}"
    fi

    # Upload with metadata
    aws s3 cp "$JAR_PATH" "$s3_path" \
        --region "$region" \
        --metadata "$metadata" \
        --cache-control "public, max-age=3600"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully published to ${region}${NC}"
        
        # Verify metadata was set
        if [ -n "${GH_REF:-}" ]; then
            local object_metadata=$(aws s3api head-object --bucket "$bucket_name" --key "$DEPLOYMENT_ARTIFACT" --region "$region" --query 'Metadata.gh_ref' --output text 2>/dev/null || echo "")
            if [ -n "$object_metadata" ] && [ "$object_metadata" != "None" ]; then
                echo -e "${GREEN}✓ Metadata verified: gh_ref=${object_metadata}${NC}"
            fi
        fi
    else
        echo -e "${RED}✗ Failed to publish to ${region}${NC}"
        return 1
    fi

    echo ""
}

# Main execution
publish() {
    # Check if artifacts already exist and prompt for confirmation
    EXISTING_REGIONS_OUTPUT=$(check_artifacts_exist)
    local check_result=$?
    
    if [ $check_result -eq 0 ]; then
        # Convert output to array
        readarray -t existing_regions_array <<< "$EXISTING_REGIONS_OUTPUT"
        if ! prompt_overwrite "${existing_regions_array[@]}"; then
            echo -e "${YELLOW}Publishing cancelled by user${NC}"
            exit 0
        fi
        echo ""
    fi

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
            echo -e "  ${GREEN}${region}:${NC} https://${bucket_name}.s3.${region}.amazonaws.com/${DEPLOYMENT_ARTIFACT}"
        done
    else
        echo -e "${YELLOW}⚠ Some regions failed to publish${NC}"
        exit 1
    fi
}

# Run main publish function
publish "$@"
