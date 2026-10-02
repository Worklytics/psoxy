#!/usr/bin/env bash
# Cloud Agent environment bootstrap for Psoxy.
#
# Idempotent: installs the build toolchain (Maven, Terraform) if missing, then
# prepares the repository (Java build + OpenNLP models, Node tool deps). Java,
# Node, Python, git and curl are provided by the base image.
#
# Downloaded tooling is integrity-checked before any privileged step:
#   - Maven is verified against the pinned upstream SHA-512 digest.
#   - Terraform is verified against HashiCorp's GPG-signed SHA256SUMS.
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

# Maven has no canonical version file in this repo, so its version is pinned here
# together with the upstream SHA-512 digest published at
# https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz.sha512
MAVEN_VERSION="3.9.10"
MAVEN_SHA512="4ef617e421695192a3e9a53b3530d803baf31f4269b26f9ab6863452d833da5530a4d04ed08c36490ad0f141b55304bceed58dbf44821153d94ae9abf34d0e1b"

# Terraform's target version is owned by infra/examples-dev/*/.terraform-version
# (read at install time) to avoid duplicating it here. Downloads are verified
# against HashiCorp's signed SHA256SUMS using this pinned signing-key fingerprint
# (https://www.hashicorp.com/.well-known/pgp-key.txt).
TERRAFORM_VERSION_FILE="infra/examples-dev/aws/.terraform-version"
HASHICORP_GPG_FINGERPRINT="C874011F0AB405110D0210553436 5D9472D7468F"
HASHICORP_GPG_FINGERPRINT="${HASHICORP_GPG_FINGERPRINT// /}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

install_maven() {
    if command -v mvn >/dev/null 2>&1; then
        printf "${INFO}Maven already present: %s${NC}\n" "$(mvn -v | head -n 1)"
        return
    fi
    printf "${INFO}Installing Apache Maven %s...${NC}\n" "$MAVEN_VERSION"
    local tmp archive
    tmp="$(mktemp -d)"
    archive="${tmp}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o "$archive"

    printf "${INFO}Verifying Maven archive (SHA-512)...${NC}\n"
    if ! printf '%s  %s\n' "$MAVEN_SHA512" "$archive" | sha512sum -c - >/dev/null 2>&1; then
        printf "${ERR}Maven archive failed SHA-512 verification; aborting.${NC}\n" >&2
        rm -rf "$tmp"
        exit 1
    fi

    sudo mkdir -p /opt
    sudo tar -xzf "$archive" -C /opt
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

    local version
    version="$(tr -d '[:space:]' < "$TERRAFORM_VERSION_FILE")"
    if [ -z "$version" ]; then
        printf "${ERR}Could not read Terraform version from %s; aborting.${NC}\n" "$TERRAFORM_VERSION_FILE" >&2
        exit 1
    fi
    printf "${INFO}Installing Terraform %s...${NC}\n" "$version"

    local tmp base zip
    tmp="$(mktemp -d)"
    base="https://releases.hashicorp.com/terraform/${version}"
    zip="terraform_${version}_linux_amd64.zip"
    curl -fsSL "${base}/${zip}" -o "${tmp}/${zip}"
    curl -fsSL "${base}/terraform_${version}_SHA256SUMS" -o "${tmp}/SHA256SUMS"
    curl -fsSL "${base}/terraform_${version}_SHA256SUMS.sig" -o "${tmp}/SHA256SUMS.sig"

    printf "${INFO}Verifying Terraform SHA256SUMS signature...${NC}\n"
    local gnupg_home
    gnupg_home="$(mktemp -d)"
    if ! curl -fsSL https://www.hashicorp.com/.well-known/pgp-key.txt \
        | GNUPGHOME="$gnupg_home" gpg --quiet --import >/dev/null 2>&1; then
        printf "${ERR}Failed to import HashiCorp signing key; aborting.${NC}\n" >&2
        rm -rf "$tmp" "$gnupg_home"
        exit 1
    fi
    if ! GNUPGHOME="$gnupg_home" gpg --with-colons --fingerprint 2>/dev/null \
        | awk -F: '/^fpr:/{print $10}' | grep -qx "$HASHICORP_GPG_FINGERPRINT"; then
        printf "${ERR}HashiCorp signing key fingerprint mismatch; aborting.${NC}\n" >&2
        rm -rf "$tmp" "$gnupg_home"
        exit 1
    fi
    if ! GNUPGHOME="$gnupg_home" gpg --quiet --verify "${tmp}/SHA256SUMS.sig" "${tmp}/SHA256SUMS" >/dev/null 2>&1; then
        printf "${ERR}Terraform SHA256SUMS signature verification failed; aborting.${NC}\n" >&2
        rm -rf "$tmp" "$gnupg_home"
        exit 1
    fi

    printf "${INFO}Verifying Terraform archive (SHA-256)...${NC}\n"
    if ! ( cd "$tmp" && grep " ${zip}\$" SHA256SUMS | sha256sum -c - >/dev/null 2>&1 ); then
        printf "${ERR}Terraform archive failed SHA-256 verification; aborting.${NC}\n" >&2
        rm -rf "$tmp" "$gnupg_home"
        exit 1
    fi

    ( cd "$tmp" && unzip -o -q "$zip" )
    sudo mv "${tmp}/terraform" /usr/local/bin/terraform
    rm -rf "$tmp" "$gnupg_home"
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
# Use 'npm ci' so the committed package-lock.json is the source of truth; a failure
# here signals a lockfile/manifest mismatch and should stop setup rather than be masked.
printf "${INFO}Installing Node tool dependencies...${NC}\n"
(cd tools/psoxy-test && npm ci --no-fund --no-audit)
(cd tools/schema-tool && npm ci --no-fund --no-audit)

printf "${SUCCESS}Psoxy Cloud Agent environment ready.${NC}\n"
