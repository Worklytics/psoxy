#!/bin/bash

# Publish Psoxy GCP JAR to GCS bucket, zipped so can be used as a Cloud Function deployment bundle
# Usage: ./publish-gcp-release-artifact.sh [version]
# If version not provided, reads from java/pom.xml

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
IMPLEMENTATION="gcp"
JAVA_SOURCE_ROOT="java/"
BUCKET_NAME="psoxy-public-artifacts"
JAR_NAME="psoxy-$IMPLEMENTATION"

# ensure current directory is the project root
if [ ! -f "java/pom.xml" ]; then
    echo -e "${RED}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
    exit 1
fi

# Get version from argument or pom.xml
if [ -n "$1" ]; then
    VERSION="$1"
else
    VERSION=$(sed -n 's|[[:space:]]*<revision>\(.*\)</revision>|\1|p' "java/pom.xml")
    if [ -z "$VERSION" ]; then
        echo -e "${RED}Error: Could not extract version from java/pom.xml${NC}"
        exit 1
    fi
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
        current_ref=$(git describe --exact-match --tags HEAD | tr -d '\n\r' | xargs)
    else
        # We're on a branch
        current_ref=$(git rev-parse --abbrev-ref HEAD | tr -d '\n\r' | xargs)
    fi
    
    # Expected tag format: v{VERSION}
    local expected_tag="v${VERSION}"
    
    # Check if we're on main branch or matching tag
    if [ "$current_ref" = "main" ]; then
        echo -e "${GREEN}✓ Running on main branch${NC}"
        return 0
    elif [ "$current_ref" = "$expected_tag" ] || [ "$current_ref" = "${VERSION}" ]; then
        echo -e "${GREEN}✓ Running on tag ${current_ref}${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ Warning: Not running on main branch or tag ${expected_tag}${NC}"
        echo -e "${YELLOW}Current git reference: ${current_ref}${NC}"
        echo -e "${YELLOW}Version: ${VERSION}${NC}"
        echo ""
        echo -e "${YELLOW}Recommended: Run from main branch or tag ${expected_tag}${NC}"
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

# Function to clean Maven repository artifacts to avoid "bad class file" errors
# Note: JDK 21 can compile Java 17 source/target correctly, but corrupted artifacts
# in the local Maven repository can cause "bad class file" errors. This cleanup
# ensures we rebuild from source rather than using potentially corrupted cached artifacts.
clean_maven_artifacts() {
    echo -e "${BLUE}Cleaning local Maven repository artifacts to avoid 'bad class file' errors...${NC}"
    
    # Get Maven local repository path (respect MAVEN_LOCAL_REPO env var if set)
    if [ -z "$MAVEN_LOCAL_REPO" ]; then
        # Try to get it from Maven settings, fallback to default
        MAVEN_LOCAL_REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null || echo "${HOME}/.m2/repository")
    fi
    
    if [ ! -d "$MAVEN_LOCAL_REPO" ]; then
        echo -e "${YELLOW}Warning: Maven local repository not found at ${MAVEN_LOCAL_REPO}, skipping cleanup${NC}"
        return
    fi
    
    # Remove psoxy artifacts that might be corrupted or incompatible
    PSOXY_ARTIFACTS=(
        "co/worklytics/psoxy"
        "com/avaulta/gateway"
    )
    
    local cleaned=false
    for artifact_path in "${PSOXY_ARTIFACTS[@]}"; do
        local full_path="${MAVEN_LOCAL_REPO}/${artifact_path}"
        if [ -d "$full_path" ]; then
            echo -e "${YELLOW}Removing ${full_path}...${NC}"
            rm -rf "$full_path"
            echo -e "${GREEN}✓ Removed ${artifact_path}${NC}"
            cleaned=true
        fi
    done
    
    if [ "$cleaned" = true ]; then
        echo -e "${GREEN}✓ Maven repository cleanup complete${NC}"
    else
        echo -e "${BLUE}No psoxy artifacts found in local repository, nothing to clean${NC}"
    fi
    echo ""
}

# Clean Maven artifacts before building to avoid "bad class file" errors
clean_maven_artifacts

# Construct JAR filename - build in isolated environment to avoid IDE conflicts
echo -e "${BLUE}Building ${GREEN}${IMPLEMENTATION}${BLUE} bundle in isolated environment...${NC}"
./tools/build-isolated.sh "$IMPLEMENTATION"
DEPLOYMENT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

JAR_PATH="java/impl/gcp/target/deployment/${DEPLOYMENT_ARTIFACT}"

# Validate JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${RED}Error: JAR file not found at ${JAR_PATH} after running build script${NC}"
    echo -e "${YELLOW}Check last-build.log for errors${NC}"
    exit 1
fi

# Create ZIP filename for GCP Cloud Functions
ZIP_FILENAME="${JAR_NAME}-${VERSION}.zip"
ZIP_PATH="/tmp/${ZIP_FILENAME}"

# Create ZIP file containing the JAR (GCP Cloud Functions require ZIP, not JAR)
echo -e "${BLUE}Creating ZIP file for GCP Cloud Functions...${NC}"
cd "$(dirname "$JAR_PATH")" && zip -j "$ZIP_PATH" "$(basename "$JAR_PATH")"
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Failed to create ZIP file${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Created ZIP file: ${ZIP_PATH}${NC}"
cd - > /dev/null

echo -e "${BLUE}Publishing Psoxy GCP ZIP version ${GREEN}${VERSION}${BLUE} to GCS bucket...${NC}"
echo -e "${BLUE}JAR file: ${GREEN}${JAR_PATH}${NC}"
echo -e "${BLUE}ZIP file: ${GREEN}${ZIP_PATH}${NC}"
echo -e "${BLUE}Bucket: ${GREEN}gs://${BUCKET_NAME}${NC}"
echo ""



# Function to check if artifact already exists in GCS
check_artifact_exists() {
    local gcs_path="gs://${BUCKET_NAME}/${ZIP_FILENAME}"
    
    if gsutil ls "$gcs_path" >/dev/null 2>&1; then
        return 0  # Artifact exists
    else
        return 1  # Artifact does not exist
    fi
}

# Function to prompt user for confirmation
prompt_overwrite() {
    local gcs_path="gs://${BUCKET_NAME}/${ZIP_FILENAME}"
    
    echo -e "${YELLOW}Warning: Artifact already exists in GCS:${NC}"
    echo -e "  ${BLUE}${gcs_path}${NC}"
    echo ""
    echo -e "${YELLOW}Do you want to overwrite it? (yes/no):${NC} "
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

# Function to publish to GCS
publish_to_gcs() {
    local gcs_path="gs://${BUCKET_NAME}/${ZIP_FILENAME}"

    echo -e "Publishing ${BLUE}${ZIP_PATH}${NC} to ${BLUE}${gcs_path}${NC}"

    # Check if bucket exists
    if ! gsutil ls "gs://${BUCKET_NAME}" >/dev/null 2>&1; then
        echo -e "${YELLOW}Warning: Bucket ${BUCKET_NAME} does not exist${NC}"
    fi

    # Upload with metadata
    gsutil cp "$ZIP_PATH" "$gcs_path"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully uploaded to GCS${NC}"

    else
        echo -e "${RED}✗ Failed to upload to GCS${NC}"
        return 1
    fi

    echo ""
}

# Main execution
main() {
    echo -e "${BLUE}=== Psoxy GCP Artifact Publisher ===${NC}"
    echo -e "${BLUE}Version: ${GREEN}${VERSION}${NC}"
    echo -e "${BLUE}Bucket: ${GREEN}gs://${BUCKET_NAME}${NC}"
    echo ""

    # Check prerequisites

    if ! command -v gsutil &> /dev/null; then
        echo -e "${RED}Error: gsutil is not installed${NC}"
        echo -e "${YELLOW}Install Google Cloud SDK from: https://cloud.google.com/sdk/docs/install${NC}"
        exit 1
    fi

    # Check gsutil version
    GSUTIL_VERSION=$(gsutil version -l 2>/dev/null | grep "gsutil version" | cut -d' ' -f3)
    if [ -z "$GSUTIL_VERSION" ]; then
        echo -e "${RED}Error: gsutil is installed but not working properly${NC}"
        exit 1
    fi
    echo -e "${BLUE}gsutil version: ${GREEN}${GSUTIL_VERSION}${NC}"

    # Check if gsutil is authenticated
    if ! gsutil ls >/dev/null 2>&1; then
        echo -e "${RED}Error: gsutil is not authenticated${NC}"
        echo -e "${YELLOW}Run 'gcloud auth login' to authenticate${NC}"
        exit 1
    fi

    # Show current GCP project
    CURRENT_PROJECT=$(gcloud config get-value project 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$CURRENT_PROJECT" ]; then
        echo -e "${BLUE}Current GCP project: ${GREEN}${CURRENT_PROJECT}${NC}"
    fi

    if ! command -v jq &> /dev/null; then
        echo -e "${RED}Error: jq is not installed${NC}"
        echo -e "${YELLOW}Install jq from: https://stedolan.github.io/jq/download/${NC}"
        exit 1
    fi

    # Check if artifact already exists and prompt for confirmation
    if check_artifact_exists; then
        if ! prompt_overwrite; then
            echo -e "${YELLOW}Publishing cancelled by user${NC}"
            exit 0
        fi
        echo ""
    fi

    # Publish to GCS
    if publish_to_gcs; then
        echo -e "${BLUE}=== Summary ===${NC}"
        echo -e "${GREEN}✓ Successfully published GCP JAR to GCS!${NC}"
        echo ""
        echo -e "${BLUE}Download URL:${NC}"
        echo -e "  ${GREEN}GCS:${NC} https://storage.googleapis.com/${BUCKET_NAME}/${ZIP_FILENAME}"
        echo ""
        echo -e "${BLUE}GCS URL for Terraform:${NC}"
        echo -e "  ${GREEN}gs://${BUCKET_NAME}/${ZIP_FILENAME}${NC}"
    else
        echo -e "${RED}✗ Failed to publish to GCS${NC}"
        exit 1
    fi
}

# Run main function
main "$@"
