#!/bin/bash
# Configure Cloud Run custom audiences on API connector services fronted by the
# external Application Load Balancer (beta).
#
# Run from the Terraform working directory that contains terraform.tfvars
# (the GCP example root), after terraform apply.
#
# Requires: bash, terraform, gcloud, jq

set -euo pipefail

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
  # shellcheck source=../set-term-colorscheme.sh
  source "$COLORSCHEME_SH"
else
  ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

printf "${INFO}Cloud Run custom audiences for the external Application Load Balancer (beta)${NC}\n\n"
printf "API connectors authenticate callers with a Google identity token. Cloud Run accepts that token only when its audience matches the service URL, or a URL you register as a custom audience.\n\n"
printf "With an external Application Load Balancer in front of the connectors, Worklytics (and local tests) mint the token for the public URL they call — ${CODE}https://<api-proxy-domain>/<function>${NC} — and sometimes for the Cloud Functions URL ${CODE}https://<region>-<project>.cloudfunctions.net/<function>${NC}. Those are not the default Cloud Run (*.run.app) audience, so Cloud Run rejects the token until you register them.\n\n"
printf "Without this step, calls through the load balancer fail authentication. Ingress can mask that as HTTP 404; a mismatched audience is often HTTP 401 or 403.\n\n"
printf "This script registers those two audiences on each API connector Cloud Run service. ${WARN}--set-custom-audiences replaces any custom audiences already set on the service.${NC}\n\n"

for cmd in terraform gcloud jq; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf "${ERR}%s is required but not installed.${NC}\n" "$cmd"
    exit 1
  fi
done

if [ ! -f "terraform.tfvars" ]; then
  printf "${ERR}No terraform.tfvars in the current directory.${NC}\n"
  printf "Change to the GCP Terraform root (the directory where you run terraform apply) and run this script again.\n"
  exit 1
fi

if ! terraform output >/dev/null 2>&1; then
  printf "${ERR}terraform output failed.${NC}\n"
  printf "Run this from an initialized Terraform directory (terraform init, and apply so outputs exist).\n"
  exit 1
fi

GCLOUD_ACCOUNT="$(gcloud config get-value account 2>/dev/null || true)"
if [ -z "$GCLOUD_ACCOUNT" ] || [ "$GCLOUD_ACCOUNT" = "(unset)" ]; then
  printf "${ERR}gcloud is not authenticated.${NC} Run ${CODE}gcloud auth login${NC} and retry.\n"
  exit 1
fi
printf "gcloud is authenticated as ${INFO}%s${NC}.\n\n" "$GCLOUD_ACCOUNT"

# Print a terraform output as JSON, or return 1 if the output is missing or null.
tf_output_json() {
  local name="$1"
  local out
  if ! out="$(terraform output -json "$name" 2>/dev/null)"; then
    return 1
  fi
  if [ -z "$out" ] || [ "$out" = "null" ]; then
    return 1
  fi
  printf '%s' "$out"
}

# Value of a top-level string assignment in terraform.tfvars: key = "value"
tfvar_string() {
  local key="$1"
  local line value
  line="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" terraform.tfvars | head -n 1 || true)"
  if [ -z "$line" ]; then
    return 1
  fi
  value="${line#*=}"
  value="${value%%#*}"
  value="$(printf '%s' "$value" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//; s/^"(.*)"$/\1/; s/^'\''(.*)'\''$/\1/')"
  if [ -z "$value" ] || [ "$value" = "null" ]; then
    return 1
  fi
  printf '%s' "$value"
}

# domain = "..." inside an external_api_alb = { ... } block (single- or multi-line).
tfvar_external_api_alb_domain() {
  awk '
    /^[[:space:]]*external_api_alb[[:space:]]*=/ { in_block=1 }
    in_block && /domain[[:space:]]*=/ {
      line=$0
      sub(/.*domain[[:space:]]*=[[:space:]]*/, "", line)
      sub(/[[:space:]]*#.*/, "", line)
      sub(/[[:space:]]*}.*/, "", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      gsub(/^"|"$/, "", line)
      gsub(/^'\''|'\''$/, "", line)
      if (line != "" && line != "null") print line
      exit
    }
    in_block && /}/ { exit }
  ' terraform.tfvars
}

PROJECT_ID=""
REGION=""
API_PROXY_DOMAIN=""
PROJECT_SOURCE=""
REGION_SOURCE=""
DOMAIN_SOURCE=""

if PROJECT_ID="$(tfvar_string gcp_project_id)"; then
  PROJECT_SOURCE="terraform.tfvars (gcp_project_id)"
fi
if out="$(tf_output_json gcp_project_id 2>/dev/null || true)" && [ -n "$out" ]; then
  parsed="$(printf '%s' "$out" | jq -r 'if type == "string" then . else empty end')"
  if [ -n "$parsed" ]; then
    PROJECT_ID="$parsed"
    PROJECT_SOURCE="terraform output gcp_project_id"
  fi
fi

if REGION="$(tfvar_string gcp_region)"; then
  REGION_SOURCE="terraform.tfvars (gcp_region)"
fi
if out="$(tf_output_json gcp_region 2>/dev/null || true)" && [ -n "$out" ]; then
  parsed="$(printf '%s' "$out" | jq -r 'if type == "string" then . else empty end')"
  if [ -n "$parsed" ]; then
    REGION="$parsed"
    REGION_SOURCE="terraform output gcp_region"
  fi
fi

if API_PROXY_DOMAIN="$(tfvar_string api_proxy_domain)"; then
  DOMAIN_SOURCE="terraform.tfvars (api_proxy_domain)"
fi
if alb_domain="$(tfvar_external_api_alb_domain)" && [ -n "$alb_domain" ]; then
  if [ -z "$API_PROXY_DOMAIN" ]; then
    API_PROXY_DOMAIN="$alb_domain"
    DOMAIN_SOURCE="terraform.tfvars (external_api_alb.domain)"
  fi
fi
if byo="$(tfvar_string api_connector_external_lb_host)"; then
  if [ -z "$API_PROXY_DOMAIN" ]; then
    API_PROXY_DOMAIN="$byo"
    DOMAIN_SOURCE="terraform.tfvars (api_connector_external_lb_host)"
  fi
fi
if out="$(tf_output_json external_api_alb 2>/dev/null || true)" && [ -n "$out" ]; then
  parsed="$(printf '%s' "$out" | jq -r '.host // empty')"
  if [ -n "$parsed" ] && [ "$parsed" != "null" ]; then
    API_PROXY_DOMAIN="$parsed"
    DOMAIN_SOURCE="terraform output external_api_alb.host"
  fi
fi

prompt_if_empty() {
  local var_name="$1"
  local label="$2"
  local current="$3"
  if [ -n "$current" ]; then
    printf -v "$var_name" '%s' "$current"
    return 0
  fi
  printf "${WARN}Could not read %s from terraform output or terraform.tfvars.${NC}\n" "$label"
  printf "Enter %s: " "$label"
  local entered
  read -r entered
  if [ -z "$entered" ]; then
    printf "${ERR}%s is required.${NC}\n" "$label"
    exit 1
  fi
  printf -v "$var_name" '%s' "$entered"
}

prompt_if_empty PROJECT_ID "GCP project id" "$PROJECT_ID"
prompt_if_empty REGION "GCP region (gcp_region)" "$REGION"
prompt_if_empty API_PROXY_DOMAIN "API proxy domain or reserved IP (api_proxy_domain)" "$API_PROXY_DOMAIN"

FUNCTIONS=()
FUNCTION_SOURCE=""
if urls="$(tf_output_json external_alb_connector_urls)"; then
  while IFS= read -r fn; do
    [ -n "$fn" ] && FUNCTIONS+=("$fn")
  done < <(printf '%s' "$urls" | jq -r 'to_entries[].value' | sed -E 's|https?://[^/]+/||; s|/$||')
  FUNCTION_SOURCE="terraform output external_alb_connector_urls"
fi

if [ "${#FUNCTIONS[@]}" -eq 0 ]; then
  if instances="$(tf_output_json api_connector_instances)"; then
    while IFS= read -r fn; do
      [ -n "$fn" ] && FUNCTIONS+=("$fn")
    done < <(printf '%s' "$instances" | jq -r 'to_entries[].value.cloud_function_name // empty')
    FUNCTION_SOURCE="terraform output api_connector_instances (cloud_function_name)"
  fi
fi

if [ "${#FUNCTIONS[@]}" -eq 0 ]; then
  printf "${ERR}No API connector functions found.${NC}\n"
  printf "Expected terraform output ${CODE}external_alb_connector_urls${NC} or ${CODE}api_connector_instances${NC}.\n"
  printf "Apply the configuration with the external Application Load Balancer enabled, then rerun this script.\n"
  exit 1
fi

printf "Resolved configuration:\n"
printf "  GCP project:      ${CODE}%s${NC}  (%s)\n" "$PROJECT_ID" "${PROJECT_SOURCE:-entered at prompt}"
printf "  Region:           ${CODE}%s${NC}  (%s)\n" "$REGION" "${REGION_SOURCE:-entered at prompt}"
printf "  API proxy domain: ${CODE}%s${NC}  (%s)\n" "$API_PROXY_DOMAIN" "${DOMAIN_SOURCE:-entered at prompt}"
printf "  Functions:        %s\n" "$FUNCTION_SOURCE"
for fn in "${FUNCTIONS[@]}"; do
  printf "    - ${CODE}%s${NC}\n" "$fn"
  printf "      audiences:\n"
  printf "        ${CODE}https://%s-%s.cloudfunctions.net/%s${NC}\n" "$REGION" "$PROJECT_ID" "$fn"
  printf "        ${CODE}https://%s/%s${NC}\n" "$API_PROXY_DOMAIN" "$fn"
done

printf "\nUpdate custom audiences on these Cloud Run services? [y/N]: "
read -r CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
  printf "Aborted. No gcloud commands were run.\n"
  exit 0
fi

for fn in "${FUNCTIONS[@]}"; do
  audiences="https://${REGION}-${PROJECT_ID}.cloudfunctions.net/${fn},https://${API_PROXY_DOMAIN}/${fn}"
  printf "\nUpdating ${INFO}%s${NC}\n" "$fn"
  gcloud run services update "$fn" \
    --project="$PROJECT_ID" \
    --region="$REGION" \
    --set-custom-audiences="$audiences"
done

printf "\n${SUCCESS}Custom audiences updated for %s service(s).${NC}\n" "${#FUNCTIONS[@]}"
