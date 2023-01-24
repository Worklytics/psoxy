#!/bin/bash

# errors halt execution
set -e

# psoxy build script to be invoked from Terraform 'external' data resource
IDENTITY_POOL_ID=$1
LOGIN_ID=$2 # expected to be an AAD clientId, etc
REGION=$3 # expected us-east-1, etc

aws cognito-identity get-open-id-token-for-developer-identity --identity-pool-id $IDENTITY_POOL_ID --logins $LOGIN_ID --region $REGION