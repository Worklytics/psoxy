#!/bin/bash

# Build Psoxy bundles in an isolated environment to avoid IDE conflicts
# Usage: ./build-isolated.sh [aws|gcp|both] [-t] [-k]
#   -t: Run tests (default: skip tests for faster builds)
#   -k: Keep isolated build directory after completion (default: cleanup)

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAVA_SOURCE_ROOT="${PROJECT_ROOT}/java"

# Parse arguments
RUN_TESTS=false
KEEP_BUILD_DIR=false
IMPLEMENTATIONS=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--test)
            RUN_TESTS=true
            shift
            ;;
        -k|--keep)
            KEEP_BUILD_DIR=true
            shift
            ;;
        aws|gcp|both)
            if [ "$1" = "both" ]; then
                IMPLEMENTATIONS=("aws" "gcp")
            else
                IMPLEMENTATIONS=("$1")
            fi
            shift
            ;;
        *)
            echo -e "${RED}Error: Unknown option: $1${NC}"
            echo "Usage: $0 [aws|gcp|both] [-t] [-k]"
            echo "  -t: Run tests (default: skip tests)"
            echo "  -k: Keep isolated build directory (default: cleanup)"
            exit 1
            ;;
    esac
done

# Default to both if not specified
if [ ${#IMPLEMENTATIONS[@]} -eq 0 ]; then
    IMPLEMENTATIONS=("aws" "gcp")
fi

# Validate we're in the right directory
if [ ! -f "${JAVA_SOURCE_ROOT}/pom.xml" ]; then
    echo -e "${RED}Error: java/pom.xml not found. Run this script from the psoxy root directory.${NC}"
    exit 1
fi

# Create isolated build directory
ISOLATED_DIR=$(mktemp -d -t psoxy-build-XXXXXX)
echo -e "${BLUE}=== Isolated Build Environment ===${NC}"
echo -e "${BLUE}Isolated directory: ${GREEN}${ISOLATED_DIR}${NC}"
echo ""

# Function to cleanup on exit
cleanup() {
    if [ "$KEEP_BUILD_DIR" = false ]; then
        echo -e "${BLUE}Cleaning up isolated build directory...${NC}"
        rm -rf "$ISOLATED_DIR"
    else
        echo -e "${YELLOW}Keeping isolated build directory: ${ISOLATED_DIR}${NC}"
    fi
}
trap cleanup EXIT

# Copy only what's needed for building: java/ directory and build script
echo -e "${BLUE}Copying required files to isolated directory...${NC}"

# Create directory structure
mkdir -p "${ISOLATED_DIR}/tools"

# Copy java/ directory (excluding target directories and IDE files)
if command -v rsync &> /dev/null; then
    rsync -a \
        --exclude='target' \
        --exclude='*.iml' \
        "${JAVA_SOURCE_ROOT%/}/" "${ISOLATED_DIR}/java/" >/dev/null
    # Copy build script
    cp "${PROJECT_ROOT}/tools/build.sh" "${ISOLATED_DIR}/tools/build.sh" >/dev/null 2>&1
else
    # Fallback: use cp
    echo -e "${YELLOW}rsync not found, using cp...${NC}"
    cp -r "${JAVA_SOURCE_ROOT}" "${ISOLATED_DIR}/" >/dev/null 2>&1
    cp "${PROJECT_ROOT}/tools/build.sh" "${ISOLATED_DIR}/tools/build.sh" >/dev/null 2>&1
    # Remove target directories and IDE files if they were copied
    find "${ISOLATED_DIR}/java" -type d -name "target" -exec rm -rf {} + 2>/dev/null || true
    find "${ISOLATED_DIR}/java" -name "*.iml" -delete 2>/dev/null || true
fi

echo -e "${GREEN}✓ Required files copied${NC}"
echo ""

# Set Maven options to avoid IDE conflicts
export MAVEN_OPTS="-Dmaven.compiler.useIncrementalCompilation=false -Dmaven.artifact.threads=1"

# Build each implementation
for IMPLEMENTATION in "${IMPLEMENTATIONS[@]}"; do
    echo -e "${BLUE}=== Building ${GREEN}${IMPLEMENTATION}${BLUE} bundle ===${NC}"
    
    ISOLATED_JAVA_ROOT="${ISOLATED_DIR}/java"
    
    # Run build script in isolated directory
    cd "${ISOLATED_DIR}"
    if [ "$RUN_TESTS" = true ]; then
        "${ISOLATED_DIR}/tools/build.sh" -d "$IMPLEMENTATION" "$ISOLATED_JAVA_ROOT"
    else
        "${ISOLATED_DIR}/tools/build.sh" -qd "$IMPLEMENTATION" "$ISOLATED_JAVA_ROOT"
    fi
    
    # Find the deployment artifact
    DEPLOYMENT_ARTIFACT=$(ls "${ISOLATED_JAVA_ROOT}/impl/${IMPLEMENTATION}/target/deployment" 2>/dev/null | grep -E "^psoxy-.*\.jar$" | head -1)
    
    if [ -z "$DEPLOYMENT_ARTIFACT" ]; then
        echo -e "${RED}✗ Failed to find deployment artifact for ${IMPLEMENTATION}${NC}"
        exit 1
    fi
    
    ISOLATED_JAR="${ISOLATED_JAVA_ROOT}/impl/${IMPLEMENTATION}/target/deployment/${DEPLOYMENT_ARTIFACT}"
    
    # Copy artifact back to original location
    ORIGINAL_TARGET="${JAVA_SOURCE_ROOT}/impl/${IMPLEMENTATION}/target/deployment"
    mkdir -p "$ORIGINAL_TARGET"
    cp "$ISOLATED_JAR" "${ORIGINAL_TARGET}/${DEPLOYMENT_ARTIFACT}"
    
    echo -e "${GREEN}✓ ${IMPLEMENTATION} bundle built successfully${NC}"
    echo -e "${BLUE}Artifact: ${GREEN}${ORIGINAL_TARGET}/${DEPLOYMENT_ARTIFACT}${NC}"
    echo ""
done

echo -e "${GREEN}=== Build Complete ===${NC}"
echo -e "${BLUE}All bundles built successfully in isolated environment${NC}"

