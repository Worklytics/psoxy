#!/bin/bash

# Publish Psoxy GCP JAR to GCS bucket, zipped so can be used as a Cloud Function deployment bundle
# Usage: ./publish-gcp-bundle.sh [--rc] [--non-interactive]
#   --rc:              Mark this as a release candidate build (adds -rc suffix to artifact name)
#   --non-interactive: Skip all interactive prompts (auto-confirm all prompts)
#
# Examples:
#   ./publish-gcp-bundle.sh                    # Read version from pom.xml
#   ./publish-gcp-bundle.sh --rc               # RC build, read version from pom.xml
#   ./publish-gcp-bundle.sh --non-interactive  # Non-interactive mode (for CI)

set -e

# Colors for output
COLORSCHEME_SH="$(dirname "$0")/../../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# Configuration (use env vars if set, otherwise defaults for local use)
IMPLEMENTATION="${IMPLEMENTATION:-gcp}"
JAVA_SOURCE_ROOT="${JAVA_SOURCE_ROOT:-java/}"
BUCKET_NAME="${BUCKET_NAME:-psoxy-public-artifacts}"
JAR_NAME="${JAR_NAME:-psoxy-$IMPLEMENTATION}"

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
        -*)
            echo -e "${ERR}Error: Unknown option: $1${NC}"
            echo "Usage: $0 [--rc] [--non-interactive]"
            shift
            exit 1
            ;;
        *)
            echo -e "${ERR}Error: Unexpected argument: $1${NC}"
            echo "Usage: $0 [--rc] [--non-interactive]"
            shift
            exit 1
            ;;
    esac
done

# ensure current directory is the project root
if [ ! -f "java/pom.xml" ]; then
    echo -e "${ERR}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
    exit 1
fi

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

# Function to clean Maven repository artifacts to avoid "bad class file" errors
# Note: JDK 21 can compile Java 17 source/target correctly, but corrupted artifacts
# in the local Maven repository can cause "bad class file" errors. This cleanup
# ensures we rebuild from source rather than using potentially corrupted cached artifacts.
clean_maven_artifacts() {
    echo -e "${INFO}Cleaning local Maven repository artifacts to avoid 'bad class file' errors...${NC}"
    
    # Get Maven local repository path (respect MAVEN_LOCAL_REPO env var if set)
    if [ -z "$MAVEN_LOCAL_REPO" ]; then
        # Try to get it from Maven settings, fallback to default
        MAVEN_LOCAL_REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null || echo "${HOME}/.m2/repository")
    fi
    
    if [ ! -d "$MAVEN_LOCAL_REPO" ]; then
        echo -e "${WARN}Warning: Maven local repository not found at ${MAVEN_LOCAL_REPO}, skipping cleanup${NC}"
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
            echo -e "${WARN}Removing ${full_path}...${NC}"
            rm -rf "$full_path"
            echo -e "${SUCCESS}✓ Removed ${artifact_path}${NC}"
            cleaned=true
        fi
    done
    
    if [ "$cleaned" = true ]; then
        echo -e "${SUCCESS}✓ Maven repository cleanup complete${NC}"
    else
        echo -e "${INFO}No psoxy artifacts found in local repository, nothing to clean${NC}"
    fi
    echo ""
}

# Clean Maven artifacts before building to avoid "bad class file" errors
clean_maven_artifacts

# Construct JAR filename
./tools/build.sh -qd "$IMPLEMENTATION" "$JAVA_SOURCE_ROOT"
DEPLOYMENT_ARTIFACT=$(ls "${JAVA_SOURCE_ROOT}impl/${IMPLEMENTATION}/target/deployment" | grep -E "^psoxy-.*\.jar$" | head -1)

JAR_PATH="java/impl/gcp/target/deployment/${DEPLOYMENT_ARTIFACT}"

# Validate JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${ERR}Error: JAR file not found at ${JAR_PATH} after running build script${NC}"
    echo -e "${WARN}Check last-build.log for errors${NC}"
    exit 1
fi

# Create ZIP filename for GCP Cloud Functions
# RC builds should have artifact name like: psoxy-gcp-0.5.15-rc.zip
# Use explicit boolean check
if [ "$IS_RC_BUILD" = "true" ] || [ "$IS_RC_BUILD" = "1" ]; then
    ZIP_FILENAME="${JAR_NAME}-${VERSION}-rc.zip"
else
    ZIP_FILENAME="${JAR_NAME}-${VERSION}.zip"
fi
ZIP_PATH="/tmp/${ZIP_FILENAME}"

# Create ZIP file containing the JAR (GCP Cloud Functions require ZIP, not JAR)
echo -e "${INFO}Creating ZIP file for GCP Cloud Functions...${NC}"
cd "$(dirname "$JAR_PATH")" && zip -j "$ZIP_PATH" "$(basename "$JAR_PATH")"
if [ $? -ne 0 ]; then
    echo -e "${ERR}Error: Failed to create ZIP file${NC}"
    exit 1
fi
echo -e "${SUCCESS}✓ Created ZIP file: ${ZIP_PATH}${NC}"
cd - > /dev/null

# Calculate SHA256 of ZIP
if command -v sha256sum &> /dev/null; then
    SHA256_HASH=$(sha256sum "$ZIP_PATH" | cut -d' ' -f1)
elif command -v shasum &> /dev/null; then
    SHA256_HASH=$(shasum -a 256 "$ZIP_PATH" | cut -d' ' -f1)
else
    SHA256_HASH=$(openssl dgst -sha256 "$ZIP_PATH" | cut -d' ' -f2)
fi
echo -e "${INFO}ZIP SHA256: ${SUCCESS}${SHA256_HASH}${NC}"

echo -e "${INFO}Publishing Psoxy GCP ZIP version ${SUCCESS}${VERSION}${INFO} to GCS bucket...${NC}"
echo -e "${INFO}JAR file: ${SUCCESS}${JAR_PATH}${NC}"
echo -e "${INFO}ZIP file: ${SUCCESS}${ZIP_PATH}${NC}"
echo -e "${INFO}Bucket: ${SUCCESS}gs://${BUCKET_NAME}${NC}"
echo ""

# Function to check if artifact already exists in GCS
check_artifact_exists() {
    local gcs_path="gs://${BUCKET_NAME}/${ZIP_FILENAME}"
    
    if gcloud storage ls "$gcs_path" >/dev/null 2>&1; then
        return 0  # Artifact exists
    else
        return 1  # Artifact does not exist
    fi
}

# Function to prompt user for confirmation
prompt_overwrite() {
    local gcs_path="gs://${BUCKET_NAME}/${ZIP_FILENAME}"
    
    echo -e "${WARN}Warning: Artifact already exists in GCS:${NC}"
    echo -e "  ${INFO}${gcs_path}${NC}"
    echo ""
    
    if [ "$NON_INTERACTIVE" = "true" ]; then
        echo -e "${INFO}Non-interactive mode: Auto-overwriting${NC}"
        return 0
    fi
    
    echo -e "${WARN}Do you want to overwrite it? (yes/no):${NC} "
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

    echo -e "Publishing ${INFO}${ZIP_PATH}${NC} to ${INFO}${gcs_path}${NC}"

    # Check if bucket exists
    if ! gcloud storage ls "gs://${BUCKET_NAME}" >/dev/null 2>&1; then
        echo -e "${WARN}Warning: Bucket ${BUCKET_NAME} does not exist${NC}"
    fi

    # Upload with metadata
    local metadata_args=(--update-custom-metadata="sha256=${SHA256_HASH}")
    if [ -n "${GH_REF:-}" ]; then
        echo -e "${INFO}Adding metadata: gh_ref=${GH_REF}${NC}"
        metadata_args+=(--update-custom-metadata="gh_ref=${GH_REF}")
    fi

    gcloud storage cp "$ZIP_PATH" "$gcs_path" "${metadata_args[@]}"

    if [ $? -eq 0 ]; then
        echo -e "${SUCCESS}✓ Successfully uploaded to GCS${NC}"
        
        # Verify metadata was set
        local sha_metadata
        sha_metadata=$(gcloud storage objects describe "$gcs_path" --format='value(customMetadata.sha256)' 2>/dev/null || echo "")
        if [ -n "$sha_metadata" ]; then
            echo -e "${SUCCESS}✓ Metadata verified: sha256=${sha_metadata}${NC}"
        fi
        if [ -n "${GH_REF:-}" ]; then
            local gh_ref_metadata
            gh_ref_metadata=$(gcloud storage objects describe "$gcs_path" --format='value(customMetadata.gh_ref)' 2>/dev/null || echo "")
            if [ -n "$gh_ref_metadata" ]; then
                echo -e "${SUCCESS}✓ Metadata verified: gh_ref=${gh_ref_metadata}${NC}"
            fi
        fi
    else
        echo -e "${ERR}✗ Failed to upload to GCS${NC}"
        return 1
    fi

    echo ""
}

# Main execution
main() {
    echo -e "${INFO}=== Psoxy GCP Artifact Publisher ===${NC}"
    echo -e "${INFO}Version: ${SUCCESS}${VERSION}${NC}"
    echo -e "${INFO}Bucket: ${SUCCESS}gs://${BUCKET_NAME}${NC}"
    echo ""

    # Check prerequisites

    if ! command -v gcloud &> /dev/null; then
        echo -e "${ERR}Error: gcloud is not installed${NC}"
        echo -e "${WARN}Install Google Cloud SDK from: https://cloud.google.com/sdk/docs/install${NC}"
        exit 1
    fi

    GCLOUD_VERSION=$(gcloud version 2>/dev/null | awk '/^Google Cloud SDK /{print $0; exit}')
    if [ -z "$GCLOUD_VERSION" ]; then
        echo -e "${ERR}Error: gcloud is installed but not working properly${NC}"
        exit 1
    fi
    echo -e "${INFO}gcloud version: ${SUCCESS}${GCLOUD_VERSION}${NC}"

    # Check if authenticated
    # In CI (GitHub Actions), OIDC authentication sets GOOGLE_APPLICATION_CREDENTIALS
    # Detect CI environment - check multiple ways
    IS_CI=false
    
    # Check for CI indicators (use parameter expansion to handle empty/unset)
    if [ "${GITHUB_ACTIONS:-}" = "true" ] || \
       [ "${GITHUB_ACTIONS:-}" = "1" ] || \
       [ -n "${CI:-}" ] || \
       [ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]; then
        IS_CI=true
    fi

    if [ "$IS_CI" = true ]; then
        echo -e "${SUCCESS}✓ Running in CI environment${NC}"
        if [ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]; then
            if [ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
                echo -e "${ERR}Error: Credentials file not found: $GOOGLE_APPLICATION_CREDENTIALS${NC}"
                exit 1
            fi
            if [ ! -r "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
                echo -e "${ERR}Error: Credentials file is not readable: $GOOGLE_APPLICATION_CREDENTIALS${NC}"
                exit 1
            fi
            echo -e "${INFO}Credentials file: ${SUCCESS}$GOOGLE_APPLICATION_CREDENTIALS${NC}"
            # Ensure gcloud uses Application Default Credentials
            export GOOGLE_APPLICATION_CREDENTIALS
        fi
        # In CI, skip the storage ls check - let actual gcloud commands fail if auth doesn't work
        echo -e "${INFO}Using Application Default Credentials for gcloud storage${NC}"
    else
        # Local execution: check traditional authentication
        if ! gcloud storage buckets list --limit=1 >/dev/null 2>&1; then
            echo -e "${ERR}Error: gcloud is not authenticated${NC}"
            echo -e "${WARN}Run 'gcloud auth login' to authenticate${NC}"
            exit 1
        fi
    fi

    # Show current GCP project
    CURRENT_PROJECT=$(gcloud config get-value project 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$CURRENT_PROJECT" ]; then
        echo -e "${INFO}Current GCP project: ${SUCCESS}${CURRENT_PROJECT}${NC}"
    fi

    if ! command -v jq &> /dev/null; then
        echo -e "${ERR}Error: jq is not installed${NC}"
        echo -e "${WARN}Install jq from: https://stedolan.github.io/jq/download/${NC}"
        exit 1
    fi

    # Check if artifact already exists and prompt for confirmation
    if check_artifact_exists; then
        if ! prompt_overwrite; then
            echo -e "${WARN}Publishing cancelled by user${NC}"
            exit 0
        fi
        echo ""
    fi

    # Publish to GCS
    if publish_to_gcs; then
        echo -e "${INFO}=== Summary ===${NC}"
        echo -e "${SUCCESS}✓ Successfully published GCP JAR to GCS!${NC}"
        echo ""
        echo -e "${INFO}Download URL:${NC}"
        echo -e "  ${SUCCESS}GCS:${NC} https://storage.googleapis.com/${BUCKET_NAME}/${ZIP_FILENAME}"
        echo ""
        echo -e "${INFO}GCS URL for Terraform:${NC}"
        echo -e "  ${SUCCESS}gs://${BUCKET_NAME}/${ZIP_FILENAME}${NC}"
        
        # Output artifact URI in standardized format for GitHub Actions summary
        echo "ARTIFACT_URI=gs://${BUCKET_NAME}/${ZIP_FILENAME}"
        echo "ARTIFACT_SHA256=${SHA256_HASH}"
    else
        echo -e "${ERR}✗ Failed to publish to GCS${NC}"
        exit 1
    fi
}

# Run main function
main "$@"
