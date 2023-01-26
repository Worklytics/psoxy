#!/bin/bash

# errors halt execution
set -e

# psoxy build script to be invoked from Terraform 'external' data resource
IDENTITY_POOL_ID=$1
LOGIN_ID=$2 # expected to be an AAD clientId, etc
REGION=$3 # expected us-east-1, etc
ROLE=$4 # ARN of the role to assume
CONNECTOR_ID=$5 # connector id, such "outlook-mail", etc
OUTPUT=$6 # Path where the file with the identity will be dropped


export $(printf "AWS_ACCESS_KEY_ID=%s AWS_SECRET_ACCESS_KEY=%s AWS_SESSION_TOKEN=%s" \
$(aws sts assume-role \
 --role-session-name="session_for_$CONNECTOR_ID" \
 --role-arn=$ROLE \
 --query "Credentials.[AccessKeyId,SecretAccessKey,SessionToken]" \
 --output text))

aws cognito-identity get-open-id-token-for-developer-identity --identity-pool-id $IDENTITY_POOL_ID --logins $LOGIN_ID --region $REGION > $OUTPUT/cognito_identity_$CONNECTOR_ID.json