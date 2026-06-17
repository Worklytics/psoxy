#!/bin/bash

# Publish Psoxy AWS JAR to multiple S3 buckets across regions
# Usage: ./publish-aws-bundle.sh [--rc] [--non-interactive] [--role-arn <arn>]
#   --rc:              Mark this as a release candidate build (adds -rc suffix to artifact name)
#   --non-interactive: Skip all interactive prompts (auto-confirm all prompts)
#   --role-arn:        IAM role to assume before publishing (required locally; CI uses OIDC)
#                      Can also be set via ROLE_ARN env var (matches GitHub secret AWS_ROLE_ARN)
#
# Examples:
#   ./publish-aws-bundle.sh
#   ./publish-aws-bundle.sh --rc               # RC build
#   ./publish-aws-bundle.sh --non-interactive  # Non-interactive mode (for CI)
#   ROLE_ARN=arn:aws:iam::123456789012:role/GithubActionPublishAgent ./publish-aws-bundle.sh --non-interactive

set -e

# Colors for output
COLORSCHEME_SH="$(dirname "$0")/../../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# Configuration (use env vars if set, otherwise defaults for local use)
ROLE_ARN="${ROLE_ARN:-}"
IMPLEMENTATION="${IMPLEMENTATION:-aws}"
JAVA_SOURCE_ROOT="${JAVA_SOURCE_ROOT:-java/}"
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
    echo -e "${ERR}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
    exit 1
fi

# Parse command-line arguments
IS_RC_BUILD=false
NON_INTERACTIVE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --rc)
            IS_RC_BUILD=true
            echo -e "${INFO}RC build flag detected${NC}"
            shift
            ;;
        --non-interactive)
            NON_INTERACTIVE=true
            echo -e "${INFO}Non-interactive mode enabled${NC}"
            shift
            ;;
        --role-arn)
            ROLE_ARN="$2"
            echo -e "${INFO}Role ARN set to: ${SUCCESS}${ROLE_ARN}${NC}"
            shift 2
            ;;
        -*)
            echo -e "${ERR}Error: Unknown option: $1${NC}"
            echo "Usage: $0 [--rc] [--non-interactive] [--role-arn <arn>]"
            exit 1
            ;;
        *)
            echo -e "${ERR}Error: Unexpected argument: $1${NC}"
            echo "Usage: $0 [--rc] [--non-interactive] [--role-arn <arn>]"
            exit 1
            ;;
    esac
done

# Get version from pom.xml
VERSION=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "java/pom.xml")
if [ -z "$VERSION" ]; then
    echo -e "${ERR}Error: Could not extract version from java/pom.xml${NC}"
    exit 1
fi

# Function to validate git branch/tag matches version requirements
validate_git_branch_or_tag() {
    # Check if git is available
    if ! command -v git &> /dev/null; then
        echo -e "${WARN}Warning: git is not installed, skipping git validation${NC}"
        return 0
    fi
    
    # Check if we're in a git repository
    if ! git rev-parse --git-dir >/dev/null 2>&1; then
        echo -e "${WARN}Warning: Not in a git repository, skipping git validation${NC}"
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
        echo -e "${SUCCESS}✓ Running on main branch${NC}"
        return 0
    elif [ "$current_ref" = "$expected_tag" ]; then
        echo -e "${SUCCESS}✓ Running on tag ${expected_tag}${NC}"
        return 0
    else
        echo -e "${WARN}⚠ Warning: Not running on main branch or tag ${expected_tag}${NC}"
        echo -e "${WARN}Current git reference: ${current_ref}${NC}"
        echo -e "${WARN}Version: ${VERSION}${NC}"
        echo ""
        echo -e "${WARN}Recommended: Run from main branch or tag ${expected_tag}${NC}"
        
        if [ "$NON_INTERACTIVE" = "true" ]; then
            echo -e "${INFO}Non-interactive mode: Auto-proceeding${NC}"
            return 0
        fi
        
        echo -e "${WARN}Do you want to proceed anyway? (yes/no):${NC} "
        read -r response
        
        case "$response" in
            [yY][eE][sS]|[yY])
                echo -e "${WARN}Proceeding with publish from ${current_ref}...${NC}"
                return 0
                ;;
            *)
                echo -e "${WARN}Publishing cancelled by user${NC}"
                exit 0
                ;;
        esac
    fi
}

# Validate git branch/tag before proceeding
validate_git_branch_or_tag
echo ""

echo -e "${INFO}=== Psoxy AWS Artifact Publisher ===${NC}"
echo -e "${INFO}Version: ${SUCCESS}${VERSION}${NC}"
echo -e "${INFO}Regions: ${SUCCESS}${REGIONS[*]}${NC}"
echo ""

# Check prerequisites
if ! command -v aws &> /dev/null; then
    echo -e "${ERR}Error: AWS CLI is not installed${NC}"
    echo -e "${WARN}Install AWS CLI from: https://aws.amazon.com/cli/${NC}"
    exit 1
fi

# Check AWS CLI version
AWS_VERSION=$(aws --version 2>/dev/null | cut -d' ' -f1 | cut -d'/' -f2)
if [ -z "$AWS_VERSION" ]; then
    echo -e "${ERR}Error: AWS CLI is installed but not working properly${NC}"
    exit 1
fi
echo -e "${INFO}AWS CLI version: ${SUCCESS}${AWS_VERSION}${NC}"

# Check if AWS CLI is configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${ERR}Error: AWS CLI is not configured/authenticated${NC}"
    echo -e "${WARN}Run 'aws configure' to set up your credentials${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${ERR}Error: jq is not installed${NC}"
    echo -e "Install with ${WARN}brew install jq${NC} or similar"
    exit 1
fi

# Show current AWS identity
CURRENT_IDENTITY=$(aws sts get-caller-identity --query 'Arn' --output text 2>/dev/null)
if [ $? -eq 0 ]; then
    echo -e "${INFO}Current AWS identity: ${SUCCESS}${CURRENT_IDENTITY}${NC}"
fi

# run build with distribution profile
./tools/build.sh -qd "$IMPLEMENTATION" "$JAVA_SOURCE_ROOT"
BUILT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

# Validate JAR exists
JAR_PATH="${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment/${BUILT_ARTIFACT}"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${ERR}Error: JAR file not found at ${JAR_PATH} after running build script${NC}"
    echo -e "${WARN}Check last-build.log for errors${NC}"
    exit 1
fi

# Calculate SHA256 of JAR
if command -v sha256sum &> /dev/null; then
    SHA256_HASH=$(sha256sum "$JAR_PATH" | cut -d' ' -f1)
elif command -v shasum &> /dev/null; then
    SHA256_HASH=$(shasum -a 256 "$JAR_PATH" | cut -d' ' -f1)
else
    SHA256_HASH=$(openssl dgst -sha256 "$JAR_PATH" | cut -d' ' -f2)
fi
echo -e "${INFO}JAR SHA256: ${SUCCESS}${SHA256_HASH}${NC}"

# Construct deployment artifact name
# RC builds should have artifact name like: psoxy-aws-0.5.15-rc.jar
# Use explicit boolean check
if [ "$IS_RC_BUILD" = "true" ] || [ "$IS_RC_BUILD" = "1" ]; then
    # Add -rc before .jar extension
    DEPLOYMENT_ARTIFACT="${BUILT_ARTIFACT%.jar}-rc.jar"
else
    DEPLOYMENT_ARTIFACT="$BUILT_ARTIFACT"
fi

echo -e "${INFO}Publishing Psoxy $IMPLEMENTATION JAR version ${SUCCESS}${VERSION}${INFO} to S3 buckets...${NC}"
echo -e "${INFO}JAR file: ${SUCCESS}${JAR_PATH}${NC}"
echo -e "${INFO}Role: ${SUCCESS}${ROLE_ARN}${NC}"
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
    
    echo -e "${WARN}Warning: Artifact already exists in the following regions:${NC}"
    for region in "${existing_regions[@]}"; do
        local bucket_name="${BUCKET_PREFIX}-${region}"
        local s3_path="s3://${bucket_name}/${DEPLOYMENT_ARTIFACT}"
        echo -e "  ${INFO}${region}:${NC} ${s3_path}"
    done
    echo ""
    
    if [ "$NON_INTERACTIVE" = "true" ]; then
        echo -e "${INFO}Non-interactive mode: Auto-overwriting${NC}"
        return 0
    fi
    
    echo -e "${WARN}Do you want to overwrite these artifacts? (yes/no):${NC} "
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
    # If no role specified, use current credentials
    if [ -z "$ROLE_ARN" ]; then
        echo -e "${INFO}No role specified to assume. Using current credentials.${NC}"
        return 0
    fi

    # Check if we are already using the correct role
    local current_arn
    current_arn=$(aws sts get-caller-identity --query 'Arn' --output text 2>/dev/null)
    
    # Check if current ARN matches the target ROLE_ARN or is an assumed role session of it
    # Expected format for assumed role: arn:aws:sts::ACCOUNT:assumed-role/ROLE_NAME/SESSION_NAME
    local role_name
    role_name=$(echo "$ROLE_ARN" | sed 's/.*:role\///')
    
    if [[ "$current_arn" == "$ROLE_ARN" ]] || [[ "$current_arn" == *":assumed-role/$role_name/"* ]]; then
        echo -e "${SUCCESS}Already authenticated as role ${role_name} (${current_arn})${NC}"
        echo -e "${WARN}Skipping assume-role step${NC}"
        echo ""
        return 0
    fi

    echo -e "${INFO}Assuming role ${SUCCESS}${ROLE_ARN}${NC}..."

    # Assume the role and get temporary credentials
    CREDENTIALS=$(aws sts assume-role \
        --role-arn "$ROLE_ARN" \
        --role-session-name "$ROLE_SESSION_NAME" \
        --output json)

    if [ $? -ne 0 ]; then
        echo -e "${ERR}Error: Failed to assume role${NC}"
        exit 1
    fi

    # Extract credentials
    export AWS_ACCESS_KEY_ID=$(echo "$CREDENTIALS" | jq -r '.Credentials.AccessKeyId')
    export AWS_SECRET_ACCESS_KEY=$(echo "$CREDENTIALS" | jq -r '.Credentials.SecretAccessKey')
    export AWS_SESSION_TOKEN=$(echo "$CREDENTIALS" | jq -r '.Credentials.SessionToken')

    echo -e "${SUCCESS}Successfully assumed role${NC}"
    echo ""
}

# Function to publish to a specific region
publish_to_region() {
    local region="$1"
    local bucket_name="${BUCKET_PREFIX}-${region}"
    local s3_path="s3://${bucket_name}/${DEPLOYMENT_ARTIFACT}"

    echo -e "${INFO}Publishing to ${SUCCESS}${region}${INFO} (${s3_path})${NC}"

    # Check if bucket exists (or is accessible)
    # Note: 'aws s3 ls' requires s3:ListBucket permission. If we only have s3:PutObject, this will fail.
    # So we treat failure here as a warning and proceed to try uploading.
    if ! aws s3 ls "s3://${bucket_name}" >/dev/null 2>&1; then
        echo -e "${WARN}Warning: Bucket ${bucket_name} not found or not listable (missing s3:ListBucket?).${NC}"
        echo -e "${WARN}Proceeding with upload attempt...${NC}"
    fi

    # Build metadata string
    local metadata="version=${VERSION},build-date=$(date -u +%Y-%m-%dT%H:%M:%SZ),sha256=${SHA256_HASH}"
    
    # Add gh_ref metadata if available (from GitHub Actions)
    if [ -n "${GH_REF:-}" ]; then
        metadata="${metadata},gh_ref=${GH_REF}"
        echo -e "${INFO}Adding metadata: gh_ref=${GH_REF}${NC}"
    fi

    # Upload with metadata
    if aws s3 cp "$JAR_PATH" "$s3_path" \
        --region "$region" \
        --metadata "$metadata" \
        --cache-control "public, max-age=3600"; then
        echo -e "${SUCCESS}✓ Successfully published to ${region}${NC}"
        
        # Verify metadata was set
        local sha_object_metadata=$(aws s3api head-object --bucket "$bucket_name" --key "$DEPLOYMENT_ARTIFACT" --region "$region" --query 'Metadata.sha256' --output text 2>/dev/null || echo "")
        if [ -n "$sha_object_metadata" ] && [ "$sha_object_metadata" != "None" ]; then
            echo -e "${SUCCESS}✓ Metadata verified: sha256=${sha_object_metadata}${NC}"
        fi
        if [ -n "${GH_REF:-}" ]; then
            local object_metadata=$(aws s3api head-object --bucket "$bucket_name" --key "$DEPLOYMENT_ARTIFACT" --region "$region" --query 'Metadata.gh_ref' --output text 2>/dev/null || echo "")
            if [ -n "$object_metadata" ] && [ "$object_metadata" != "None" ]; then
                echo -e "${SUCCESS}✓ Metadata verified: gh_ref=${object_metadata}${NC}"
            fi
        fi
    else
        echo -e "${ERR}✗ Failed to publish to ${region}${NC}"
        return 1
    fi

    echo ""
}

# Main execution
publish() {
    # Check if artifacts already exist and prompt for confirmation
    # Use set +e because check_artifacts_exist returns 1 if NO artifacts exist (normal case)
    # which would otherwise cause the script to exit immediately
    set +e
    EXISTING_REGIONS_OUTPUT=$(check_artifacts_exist)
    local check_result=$?
    set -e
    
    if [ $check_result -eq 0 ]; then
        # Convert output to array
        readarray -t existing_regions_array <<< "$EXISTING_REGIONS_OUTPUT"
        if ! prompt_overwrite "${existing_regions_array[@]}"; then
            echo -e "${WARN}Publishing cancelled by user${NC}"
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
            success_count=$((success_count + 1))
        fi
    done

    echo -e "${INFO}=== Summary ===${NC}"
    echo -e "${INFO}Successfully published to ${SUCCESS}${success_count}/${total_regions}${INFO} regions${NC}"

    if [ $success_count -eq $total_regions ]; then
        echo -e "${SUCCESS}✓ All regions published successfully!${NC}"
        echo ""
        echo -e "${INFO}Download URLs:${NC}"
        for region in "${REGIONS[@]}"; do
            local bucket_name="${BUCKET_PREFIX}-${region}"
            local artifact_url="https://${bucket_name}.s3.${region}.amazonaws.com/${DEPLOYMENT_ARTIFACT}"
            echo -e "  ${SUCCESS}${region}:${NC} ${artifact_url}"
            # Output artifact URI in standardized format for GitHub Actions summary
            echo "ARTIFACT_URI_${region}=${artifact_url}"
        done
        echo "ARTIFACT_SHA256=${SHA256_HASH}"
    else
        echo -e "${WARN}⚠ Some regions failed to publish${NC}"
        exit 1
    fi
}

# Run main publish function
publish "$@"
