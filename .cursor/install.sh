#!/usr/bin/env bash
# Cloud Agent environment bootstrap for Psoxy.
#
# Idempotent: installs the build toolchain (Maven, Terraform) if missing, then
# prepares the repository (Java build + OpenNLP models, Node tool deps). Java,
# Node, Python, git and curl are provided by the base image.
set -euo pipefail

# Use semantic colors dynamically based on terminal capability
if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
    ERR=$(tput setaf 1)
    SUCCESS=$(tput setaf 2)
    WARN=$(tput setaf 3)
    INFO=$(tput setaf 4)
    NC=$(tput sgr0)
else
    ERR='\033[0;31m'
    SUCCESS='\033[0;32m'
    WARN='\033[1;33m'
    INFO='\033[0;34m'
    NC='\033[0m'
fi

MAVEN_VERSION="3.9.10"
TERRAFORM_VERSION="1.9.8"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

install_maven() {
    if command -v mvn >/dev/null 2>&1; then
        printf "${INFO}Maven already present: %s${NC}\n" "$(mvn -v | head -n 1)"
        return
    fi
    printf "${INFO}Installing Apache Maven %s...${NC}\n" "$MAVEN_VERSION"
    local tmp
    tmp="$(mktemp -d)"
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o "${tmp}/maven.tar.gz"
    sudo mkdir -p /opt
    sudo tar -xzf "${tmp}/maven.tar.gz" -C /opt
    sudo ln -sfn "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven
    sudo ln -sfn /opt/maven/bin/mvn /usr/local/bin/mvn
    rm -rf "$tmp"
    printf "${SUCCESS}Maven installed: %s${NC}\n" "$(mvn -v | head -n 1)"
}

install_terraform() {
    if command -v terraform >/dev/null 2>&1; then
        printf "${INFO}Terraform already present: %s${NC}\n" "$(terraform -version | head -n 1)"
        return
    fi
    printf "${INFO}Installing Terraform %s...${NC}\n" "$TERRAFORM_VERSION"
    local tmp
    tmp="$(mktemp -d)"
    curl -fsSL "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip" -o "${tmp}/terraform.zip"
    (cd "$tmp" && unzip -o -q terraform.zip)
    sudo mv "${tmp}/terraform" /usr/local/bin/terraform
    rm -rf "$tmp"
    printf "${SUCCESS}Terraform installed: %s${NC}\n" "$(terraform -version | head -n 1)"
}

install_maven
install_terraform

# Fetch OpenNLP models (required by gateway-core sentence-metadata tests / runtime).
printf "${INFO}Fetching OpenNLP models...${NC}\n"
./tools/fetch-opennlp-models.sh

# Build all Java modules (warms the local Maven repository; skips tests for speed).
printf "${INFO}Building Java modules...${NC}\n"
(cd java && mvn clean install -DskipTests -T 2C -Dversions.logOutput=false)

# Install Node dependencies for the CLI testing / schema tools.
printf "${INFO}Installing Node tool dependencies...${NC}\n"
(cd tools/psoxy-test && npm ci --no-fund --no-audit || npm install --no-fund --no-audit)
(cd tools/schema-tool && npm ci --no-fund --no-audit || npm install --no-fund --no-audit)

printf "${SUCCESS}Psoxy Cloud Agent environment ready.${NC}\n"
