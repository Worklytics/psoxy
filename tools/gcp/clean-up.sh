#!/bin/bash

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color
printf "This is an ${RED}UNSUPPORTED${NC} tool to cleanup (destroy) Psoxy infra without using terraform."
printf "Use with caution!!\n"
printf "It exists to help in various dev cases, where you've lost your terraform state.\n"

USAGE="Usage: $0 <project_id> <env_prefix>"

PROJECT_ID=$1
if [ -z "$PROJECT_ID" ]; then
  printf "${RED}Project ID not provided. Exiting.${NC}\n$USAGE\n"
  exit 1
fi

ENV_PREFIX=$2
if [ -z "$ENV_PREFIX" ]; then
  printf "${RED}Environment prefix not provided. Exiting.${NC}\n$USAGE\n"
  exit 1
fi


## GCP secrets
secrets=$(gcloud secrets list --format='value(name)' --project=$PROJECT_ID | grep "^${ENV_PREFIX}")

if [ -z "$secrets" ]; then
  printf "No GCP secrets found with the prefix ${BLUE}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following secrets:\n"
  printf "${BLUE}$secrets${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Exiting.\n"
    exit 1
  fi

  # Delete the secrets
  for secret in $secrets; do
      printf "Deleting secret: ${BLUE}%s${NC}\n" "$secret"
      gcloud secrets delete "$secret" --quiet --project=$PROJECT_ID
  done

  printf "${GREEN}All secrets with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
fi

## service accounts
service_accounts=$(gcloud iam service-accounts list --format='value(email)' --project=$PROJECT_ID | grep "^${ENV_PREFIX}")

if [ -z "$service_accounts" ]; then
  printf "No service accounts found with the prefix ${BLUE}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following service accounts:\n"
  printf "${BLUE}$service_accounts${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Exiting.\n"
    exit 1
  fi

  # Delete the service accounts
  for service_account in $service_accounts; do
      printf "Deleting service account: ${BLUE}%s${NC}\n" "$service_account"
      gcloud iam service-accounts delete "$service_account" --quiet --project=$PROJECT_ID
  done

  printf "${GREEN}All service accounts with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
fi

## cloud functions
cloud_functions=$(gcloud functions list --format='value(name)' --project=$PROJECT_ID | grep "^${ENV_PREFIX}")

if [ -z "$cloud_functions" ]; then
  printf "No cloud functions found with the prefix ${BLUE}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following cloud functions:\n"
  printf "${BLUE}$cloud_functions${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Exiting.\n"
    exit 1
  fi

  # Delete the cloud functions
  for cloud_function in $cloud_functions; do
      printf "Deleting cloud function: ${BLUE}%s${NC}\n" "$cloud_function"
      gcloud functions delete "$cloud_function" --quiet --project=$PROJECT_ID
  done

  printf "${GREEN}All cloud functions with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
fi

## GCS buckets
buckets=$(gsutil ls -p $PROJECT_ID | grep "^gs://${ENV_PREFIX}")

if [ -z "$buckets" ]; then
  printf "No GCS buckets found with the prefix ${BLUE}%s${NC}\n" "$ENV_PREFIX"
else
  printf "You are about to delete the following GCS buckets:\n"
  printf "${BLUE}$buckets${NC}\n"

  printf "Are you sure? (y/n): "
  read -r CONFIRM
  if [ "$CONFIRM" != "y" ]; then
    printf "Exiting.\n"
    exit 1
  fi

  # Delete the buckets
  for bucket in $buckets; do
      printf "Deleting bucket: ${BLUE}%s${NC}\n" "$bucket"
      gsutil rm -r "$bucket"
  done

  printf "${GREEN}All GCS buckets with prefix %s have been deleted.${NC}\n" "$ENV_PREFIX"
fi



