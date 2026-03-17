#!/bin/bash

# Script to generate Software Bill of Materials (SBOM) for AWS and GCP implementations
# Uses CycloneDX Maven plugin to generate SBOMs in CycloneDX format

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

# Check if running from root of checkout
if [ ! -f "java/pom.xml" ]; then
  printf "${ERR}java/pom.xml not found. You should run this script from root of psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

CHECKOUT_ROOT=$(pwd)

# If not running in CI, use a local Maven repository to avoid polluting the global cache
if [ -z "$CI" ]; then
    mkdir -p "${CHECKOUT_ROOT}/.m2/repository"
    export MAVEN_OPTS="-Dmaven.repo.local=${CHECKOUT_ROOT}/.m2/repository"
    printf "${INFO}Running locally (not in CI). Using local maven repository at ${CHECKOUT_ROOT}/.m2/repository${NC}\n"
fi

printf "Generating Software Bill of Materials (SBOM) for AWS and GCP implementations...\n\n"

# Build AWS module with verify phase to generate SBOM
printf "${INFO}Building AWS module and generating SBOM...${NC}\n"
${CHECKOUT_ROOT}/tools/build.sh -q aws "${CHECKOUT_ROOT}/java/"
mvn -f "${CHECKOUT_ROOT}/java/impl/aws/pom.xml" clean verify -DskipTests -Dmaven.deploy.skip=false
if [ $? -ne 0 ]; then
  printf "${ERR}Failed to build AWS module. Exiting.${NC}\n"
  exit 1
fi

# Check if SBOM was generated
if [ ! -f "${CHECKOUT_ROOT}/java/impl/aws/target/sbom.json" ]; then
  printf "${ERR}AWS SBOM not found at target/sbom.json. Exiting.${NC}\n"
  exit 1
fi

# Copy AWS SBOM to docs
printf "${SUCCESS}AWS SBOM generated successfully.${NC}\n"
mkdir -p "${CHECKOUT_ROOT}/docs/aws"
cp "${CHECKOUT_ROOT}/java/impl/aws/target/sbom.json" "${CHECKOUT_ROOT}/docs/aws/sbom.json"
printf "AWS SBOM copied to ${INFO}docs/aws/sbom.json${NC}\n\n"

# Build GCP module with verify phase to generate SBOM
printf "${INFO}Building GCP module and generating SBOM...${NC}\n"
${CHECKOUT_ROOT}/tools/build.sh -q gcp "${CHECKOUT_ROOT}/java/"
mvn -f "${CHECKOUT_ROOT}/java/impl/gcp/pom.xml" clean verify -DskipTests -Dmaven.deploy.skip=false
if [ $? -ne 0 ]; then
  printf "${ERR}Failed to build GCP module. Exiting.${NC}\n"
  exit 1
fi

# Check if SBOM was generated
if [ ! -f "${CHECKOUT_ROOT}/java/impl/gcp/target/sbom.json" ]; then
  printf "${ERR}GCP SBOM not found at target/sbom.json. Exiting.${NC}\n"
  exit 1
fi

# Copy GCP SBOM to docs
printf "${SUCCESS}GCP SBOM generated successfully.${NC}\n"
mkdir -p "${CHECKOUT_ROOT}/docs/gcp"
cp "${CHECKOUT_ROOT}/java/impl/gcp/target/sbom.json" "${CHECKOUT_ROOT}/docs/gcp/sbom.json"
printf "GCP SBOM copied to ${INFO}docs/gcp/sbom.json${NC}\n\n"

printf "${SUCCESS}SBOM generation complete!${NC}\n"
printf "Generated files:\n"
printf "  - ${INFO}docs/aws/sbom.json${NC}\n"
printf "  - ${INFO}docs/gcp/sbom.json${NC}\n"
