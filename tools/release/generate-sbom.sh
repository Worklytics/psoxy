#!/bin/bash

# Script to generate Software Bill of Materials (SBOM) for AWS and GCP implementations
# Uses CycloneDX Maven plugin to generate SBOMs in CycloneDX format

RED='\e[0;31m'
GREEN='\e[0;32m'
BLUE='\e[0;34m'
NC='\e[0m' # No Color

# Check if running from root of checkout
if [ ! -f "java/pom.xml" ]; then
  printf "${RED}java/pom.xml not found. You should run this script from root of psoxy checkout. Exiting.${NC}\n"
  exit 1
fi

CHECKOUT_ROOT=$(pwd)

printf "Generating Software Bill of Materials (SBOM) for AWS and GCP implementations...\n\n"

# Build AWS module with verify phase to generate SBOM
printf "${BLUE}Building AWS module and generating SBOM...${NC}\n"
${CHECKOUT_ROOT}/tools/build.sh -q aws "${CHECKOUT_ROOT}/java/"
mvn -f "${CHECKOUT_ROOT}/java/impl/aws/pom.xml" clean verify -DskipTests -Dmaven.deploy.skip=false
if [ $? -ne 0 ]; then
  printf "${RED}Failed to build AWS module. Exiting.${NC}\n"
  exit 1
fi

# Check if SBOM was generated
if [ ! -f "${CHECKOUT_ROOT}/java/impl/aws/target/sbom.json" ]; then
  printf "${RED}AWS SBOM not found at target/sbom.json. Exiting.${NC}\n"
  exit 1
fi

# Copy AWS SBOM to docs
printf "${GREEN}AWS SBOM generated successfully.${NC}\n"
mkdir -p "${CHECKOUT_ROOT}/docs/aws"
cp "${CHECKOUT_ROOT}/java/impl/aws/target/sbom.json" "${CHECKOUT_ROOT}/docs/aws/sbom.json"
printf "AWS SBOM copied to ${BLUE}docs/aws/sbom.json${NC}\n\n"

# Build GCP module with verify phase to generate SBOM
printf "${BLUE}Building GCP module and generating SBOM...${NC}\n"
${CHECKOUT_ROOT}/tools/build.sh -q gcp "${CHECKOUT_ROOT}/java/"
mvn -f "${CHECKOUT_ROOT}/java/impl/gcp/pom.xml" clean verify -DskipTests -Dmaven.deploy.skip=false
if [ $? -ne 0 ]; then
  printf "${RED}Failed to build GCP module. Exiting.${NC}\n"
  exit 1
fi

# Check if SBOM was generated
if [ ! -f "${CHECKOUT_ROOT}/java/impl/gcp/target/sbom.json" ]; then
  printf "${RED}GCP SBOM not found at target/sbom.json. Exiting.${NC}\n"
  exit 1
fi

# Copy GCP SBOM to docs
printf "${GREEN}GCP SBOM generated successfully.${NC}\n"
mkdir -p "${CHECKOUT_ROOT}/docs/gcp"
cp "${CHECKOUT_ROOT}/java/impl/gcp/target/sbom.json" "${CHECKOUT_ROOT}/docs/gcp/sbom.json"
printf "GCP SBOM copied to ${BLUE}docs/gcp/sbom.json${NC}\n\n"


printf "${GREEN}SBOM generation complete!${NC}\n"
printf "Generated files:\n"
printf "  - ${BLUE}docs/aws/sbom.json${NC}\n"
printf "  - ${BLUE}docs/gcp/sbom.json${NC}\n"
