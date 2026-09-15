#!/bin/bash
# Preflight for infra/examples-dev/{aws,gcp} ./apply.
# Verifies terraform.tfvars, the configured Terraform backend, and cloud CLI
# auth for AWS / Google / Azure as relevant to this example.
#
# Run from an examples-dev terraform directory. Exits 1 if a required check fails.

set -euo pipefail

COLORSCHEME_SH="$(cd "$(dirname "$0")" && pwd)/set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=set-term-colorscheme.sh
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

FAILED=0

fail() {
  printf "  ${ERR}%s${NC}\n" "$1"
  FAILED=1
}

ok() {
  printf "  ${SUCCESS}%s${NC}\n" "$1"
}

info() {
  printf "  ${INFO}%s${NC}\n" "$1"
}

warn() {
  printf "  ${WARN}%s${NC}\n" "$1"
}

# Uncommented terraform.tfvars assignment, first match; empty if missing.
tfvar_value() {
  local name="$1"
  local line
  line="$(grep -E "^[[:space:]]*${name}[[:space:]]*=" terraform.tfvars 2>/dev/null | head -1 || true)"
  if [ -z "$line" ]; then
    return 0
  fi
  printf '%s' "$line" | cut -d'=' -f2- | tr -d ' "' | xargs
}

tfvar_set() {
  local value
  value="$(tfvar_value "$1")"
  [ -n "$value" ] && [ "$value" != "null" ]
}

# First uncommented backend type in backend.tf (local, gcs, s3, remote, ...).
backend_type_from_tf() {
  if [ ! -f backend.tf ]; then
    printf ''
    return 0
  fi
  grep -E '^[[:space:]]*backend "' backend.tf 2>/dev/null | head -1 | sed 's/.*backend "\([^"]*\)".*/\1/' || true
}

initialized_backend_type() {
  local state=".terraform/terraform.tfstate"
  if [ ! -f "$state" ]; then
    printf ''
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("backend",{}).get("type",""))' "$state" 2>/dev/null || true
  else
    grep -m1 '"type":' "$state" | sed 's/.*"type":[[:space:]]*"\([^"]*\)".*/\1/' || true
  fi
}

initialized_backend_bucket() {
  local state=".terraform/terraform.tfstate"
  if [ ! -f "$state" ]; then
    printf ''
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json,sys; c=json.load(open(sys.argv[1])).get("backend",{}).get("config") or {}; print(c.get("bucket") or c.get("hostname") or "")' "$state" 2>/dev/null || true
  fi
}

initialized_backend_prefix() {
  local state=".terraform/terraform.tfstate"
  if [ ! -f "$state" ]; then
    printf ''
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json,sys; c=json.load(open(sys.argv[1])).get("backend",{}).get("config") or {}; print(c.get("prefix") or c.get("key") or "")' "$state" 2>/dev/null || true
  fi
}

printf "${INFO}Preflight: Terraform config and cloud auth${NC}\n\n"

if [ ! -f terraform.tfvars ]; then
  fail "No terraform.tfvars in ${PWD}."
  printf "  Create one (./init) or copy your personal overlay into this worktree.\n"
  exit 1
fi
ok "terraform.tfvars: ${PWD}/terraform.tfvars"

if [ ! -f backend.tf ]; then
  fail "No backend.tf in ${PWD}."
  exit 1
fi

BACKEND_TF="$(backend_type_from_tf)"
INIT_BACKEND="$(initialized_backend_type)"
INIT_BUCKET="$(initialized_backend_bucket)"
INIT_PREFIX="$(initialized_backend_prefix)"

if [ -z "$BACKEND_TF" ]; then
  fail "Could not parse an uncommented backend block from backend.tf."
else
  info "backend.tf: ${BACKEND_TF}"
fi

if [ -z "$INIT_BACKEND" ]; then
  fail "Terraform is not initialized (.terraform/terraform.tfstate missing). Run ${CODE}terraform init${NC}."
else
  if [ -n "$INIT_BUCKET" ]; then
    ok "initialized backend: ${INIT_BACKEND} (${INIT_BUCKET} / ${INIT_PREFIX})"
  else
    info "initialized backend: ${INIT_BACKEND}"
  fi
fi

if [ -n "$BACKEND_TF" ] && [ -n "$INIT_BACKEND" ] && [ "$BACKEND_TF" != "$INIT_BACKEND" ]; then
  fail "backend.tf is ${CODE}${BACKEND_TF}${NC} but this directory is initialized as ${CODE}${INIT_BACKEND}${NC}."
  printf "  Run ${CODE}terraform init -reconfigure${NC} so apply uses the backend in backend.tf.\n"
fi

if [ "$BACKEND_TF" = "local" ] || [ "$INIT_BACKEND" = "local" ]; then
  warn "Local Terraform state will not be shared across worktrees or machines."
  printf "  For a personal examples-dev overlay, point backend.tf at a remote bucket and run ${CODE}terraform init -reconfigure${NC}.\n"
fi

NEED_AWS=0
NEED_GOOGLE=0
NEED_AZURE=0

if grep -q 'provider "aws"' ./*.tf 2>/dev/null || tfvar_set aws_account_id; then
  NEED_AWS=1
fi
if grep -q 'provider "google"' ./*.tf 2>/dev/null \
    || tfvar_set gcp_project_id \
    || tfvar_set google_workspace_gcp_project_id \
    || [ "$BACKEND_TF" = "gcs" ] \
    || [ "$INIT_BACKEND" = "gcs" ]; then
  NEED_GOOGLE=1
fi
if grep -q 'provider "azuread"' ./*.tf 2>/dev/null || grep -q 'provider "azurerm"' ./*.tf 2>/dev/null; then
  if tfvar_set msft_tenant_id; then
    NEED_AZURE=1
  fi
fi

printf "\n"

if [ "$NEED_AWS" -eq 1 ]; then
  printf "AWS\n"
  if ! command -v aws >/dev/null 2>&1; then
    fail "aws CLI is not installed."
  elif ! aws sts get-caller-identity >/dev/null 2>&1; then
    fail "AWS CLI is not authenticated. Run ${CODE}aws sso login${NC} or export credentials."
  else
    CALLER="$(aws sts get-caller-identity --query Arn --output text 2>/dev/null || true)"
    ok "authenticated as ${CALLER}"
    ASSUME_ROLE="$(tfvar_value aws_assume_role_arn)"
    if [ -n "$ASSUME_ROLE" ] && [ "$ASSUME_ROLE" != "null" ]; then
      if aws sts assume-role --role-arn "$ASSUME_ROLE" --role-session-name "examples-dev-apply-preflight" >/dev/null 2>&1; then
        ok "can assume ${ASSUME_ROLE}"
      else
        fail "cannot assume ${CODE}${ASSUME_ROLE}${NC} from terraform.tfvars. Check trust policy and current credentials."
      fi
    fi
  fi
  printf "\n"
fi

if [ "$NEED_GOOGLE" -eq 1 ]; then
  printf "Google Cloud\n"
  if ! command -v gcloud >/dev/null 2>&1; then
    fail "gcloud CLI is not installed."
  else
    if ! gcloud auth print-access-token >/dev/null 2>&1; then
      fail "gcloud user credentials are missing or expired. Run ${CODE}gcloud auth login${NC}."
    else
      ACCOUNT="$(gcloud config get-value account 2>/dev/null || true)"
      ok "gcloud user: ${ACCOUNT}"
    fi
    if ! gcloud auth application-default print-access-token >/dev/null 2>&1; then
      fail "Application Default Credentials are missing or expired (Terraform Google provider / GCS backend). Run ${CODE}gcloud auth application-default login${NC}."
    else
      ok "application-default credentials are valid"
    fi
    PROJECT="$(tfvar_value gcp_project_id)"
    if [ -z "$PROJECT" ]; then
      PROJECT="$(tfvar_value google_workspace_gcp_project_id)"
    fi
    if [ -n "$PROJECT" ]; then
      info "tfvars GCP project: ${PROJECT}"
    fi
  fi
  printf "\n"
fi

azure_auth_hint() {
  if [ -e ./az-auth ]; then
    printf './az-auth'
  elif [ -e ./auth ]; then
    printf './auth'
  else
    printf '../../../tools/az-auth.sh'
  fi
}

if [ "$NEED_AZURE" -eq 1 ]; then
  printf "Azure\n"
  AZ_HINT="$(azure_auth_hint)"
  if [ -d "${PWD}/.azure" ]; then
    export AZURE_CONFIG_DIR="${PWD}/.azure"
    info "using AZURE_CONFIG_DIR=${AZURE_CONFIG_DIR}"
  else
    warn "No .azure directory; using the default Azure CLI profile (may be a different tenant)."
    printf "  Run ${CODE}%s${NC} to sandbox auth for this example's ${CODE}msft_tenant_id${NC}.\n" "$AZ_HINT"
  fi
  if ! command -v az >/dev/null 2>&1; then
    fail "Azure CLI is not installed."
  elif ! az account show >/dev/null 2>&1; then
    fail "Azure CLI is not authenticated but ${CODE}msft_tenant_id${NC} is set. Run ${CODE}${AZ_HINT}${NC}."
  else
    AZ_TENANT="$(az account show --query tenantId -o tsv 2>/dev/null || true)"
    TF_TENANT="$(tfvar_value msft_tenant_id)"
    ok "authenticated; tenant ${AZ_TENANT}"
    if [ -n "$TF_TENANT" ] && [ "$AZ_TENANT" != "$TF_TENANT" ]; then
      fail "Azure tenant ${AZ_TENANT} does not match terraform.tfvars msft_tenant_id=${TF_TENANT}. Run ${AZ_HINT}."
    fi
  fi
  printf "\n"
fi

if [ "$FAILED" -ne 0 ]; then
  printf "${ERR}Preflight failed.${NC} Fix the issues above before applying.\n"
  exit 1
fi

printf "${SUCCESS}Preflight passed.${NC}\n"
