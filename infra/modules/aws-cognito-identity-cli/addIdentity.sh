#!/bin/bash

# errors halt execution
set -e

# psoxy build script to be invoked from Terraform 'external' data resource
IDENTITY_POOL_ID=$1
LOGIN_ID=$2 # expected to be an AAD clientId, etc
REGION=$3 # expected us-east-1, etc
CONNECTOR_ID=$4 # connector id, such "outlook-mail", etc
ROLE=$5 # ARN of the role to assume; can be empty

if test $ROLE
then
  # defensive; AWS_SECURITY_TOKEN is legacy
  unset AWS_SECURITY_TOKEN

  export $(printf "AWS_ACCESS_KEY_ID=%s AWS_SECRET_ACCESS_KEY=%s AWS_SESSION_TOKEN=%s" \
  $(aws sts assume-role \
   --role-session-name="session_for_$CONNECTOR_ID" \
   --role-arn=$ROLE \
   --query "Credentials.[AccessKeyId,SecretAccessKey,SessionToken]" \
   --output text))
fi

aws cognito-identity get-open-id-token-for-developer-identity --identity-pool-id $IDENTITY_POOL_ID --logins $LOGIN_ID --region $REGION
