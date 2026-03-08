#!/bin/bash

COLORSCHEME_SH="$(dirname "$0")/../set-term-colorscheme.sh"
if [ -f "$COLORSCHEME_SH" ]; then
    source "$COLORSCHEME_SH"
else
    ERR='\033[0;31m'; SUCCESS='\033[0;32m'; WARN='\033[1;33m'; INFO='\033[0;34m'; CODE='\033[0;36m'; NC='\033[0m'
fi

printf "This is an ${ERR}UNSUPPORTED${NC} tool to cleanup (destroy) Psoxy infra without using terraform."
printf "Use with caution!!\n"
printf "It exists to help in various dev cases, where you've lost your terraform state.\n"

USAGE="Usage: $0 <project_id> <env_prefix>"

PROJECT_ID=$1
if [ -z "$PROJECT_ID" ]; then
  printf "${ERR}Project ID not provided. Exiting.${NC}\n$USAGE\n"
  exit 1
fi

ENV_PREFIX=$2
if [ -z "$ENV_PREFIX" ]; then
  printf "${ERR}Environment prefix not provided. Exiting.${NC}\n$USAGE\n"
  exit 1
fi

## GCP secrets
secrets=$(gcloud secrets list --format='value(name)' --project=$PROJECT_ID | grep "^${ENV_PREFIX}")

if [ -z "$secrets" ]; then
  printf "No GCP secrets found with the prefix ${INFO}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following secrets:\n"
  printf "${INFO}$secrets${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Skipping.\n"
  else
    # Delete the secrets
    for secret in $secrets; do
        printf "Deleting secret: ${INFO}%s${NC}\n" "$secret"
        gcloud secrets delete "$secret" --quiet --project=$PROJECT_ID
    done

    printf "${SUCCESS}All secrets with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
  fi
fi

## service accounts
service_accounts=$(gcloud iam service-accounts list --format='value(email)' --project=$PROJECT_ID | grep "^${ENV_PREFIX}")

if [ -z "$service_accounts" ]; then
  printf "No service accounts found with the prefix ${INFO}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following service accounts:\n"
  printf "${INFO}$service_accounts${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Skipping.\n"
  else
    # Delete the service accounts
    for service_account in $service_accounts; do
        printf "Deleting service account: ${INFO}%s${NC}\n" "$service_account"
        gcloud iam service-accounts delete "$service_account" --quiet --project=$PROJECT_ID
    done

    printf "${SUCCESS}All service accounts with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
  fi
fi

## cloud functions
cloud_functions=$(gcloud functions list --format='value(name)' --project=$PROJECT_ID | grep "^${ENV_PREFIX}")

if [ -z "$cloud_functions" ]; then
  printf "No cloud functions found with the prefix ${INFO}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following cloud functions:\n"
  printf "${INFO}$cloud_functions${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Skipping.\n"
  else
    # Delete the cloud functions
    for cloud_function in $cloud_functions; do
        printf "Deleting cloud function: ${INFO}%s${NC}\n" "$cloud_function"
        gcloud functions delete "$cloud_function" --quiet --project=$PROJECT_ID
    done

    printf "${SUCCESS}All cloud functions with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
  fi
fi

## GCS buckets
buckets=$(gsutil ls -p $PROJECT_ID | grep "^gs://${ENV_PREFIX}")

if [ -z "$buckets" ]; then
  printf "No GCS buckets found with the prefix ${INFO}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following GCS buckets:\n"
  printf "${INFO}$buckets${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Skipping.\n"
  else
    # Delete the buckets
    for bucket in $buckets; do
        printf "Deleting bucket: ${INFO}%s${NC}\n" "$bucket"
        gsutil rm -r "$bucket"
    done

    printf "${SUCCESS}All GCS buckets with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
  fi
fi

# IAM roles
ROLE_PREFIX="${ENV_PREFIX//-/_}"
roles=$(gcloud iam roles list --project=$PROJECT_ID --format='value(name)' | grep "${ROLE_PREFIX}")

if [ -z "$roles" ]; then
  printf "No IAM roles found with the prefix ${INFO}%s${NC}\n" "$ROLE_PREFIX"
else
  printf "You are about to delete the following IAM roles:\n"
  printf "${INFO}$roles${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Skipping.\n"
  else
    # Delete the roles
    for role in $roles; do
        printf "Deleting role: ${INFO}%s${NC}\n" "$role"
        role_id=$(echo "$role" | sed 's/.*\/\(.*\)/\1/')
        gcloud iam roles delete "$role_id" --quiet --project=$PROJECT_ID
    done

    printf "${SUCCESS}All IAM roles with prefix %s have been deleted.${NC}\n" "$ROLE_PREFIX"
  fi
fi

