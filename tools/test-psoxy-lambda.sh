#!/bin/bash
ROLE_ARN=$1
TEST_URL=$2

echo "Assuming role $ROLE_ARN"
aws sts assume-role --role-arn $ROLE_ARN --duration 900 --role-session-name lambda_test --output json > token.json
CALLER_ACCESS_KEY_ID=`cat token.json| jq -r '.Credentials.AccessKeyId'`
CALLER_SECRET_ACCESS_KEY=`cat token.json| jq -r '.Credentials.SecretAccessKey'`
CALLER_SESSION_TOKEN=`cat token.json| jq -r '.Credentials.SessionToken'`
rm token.json
echo "Calling proxy..."
echo "Request: $TEST_URL"
echo -e "Response: \u21b4"
awscurl --service execute-api --access_key $CALLER_ACCESS_KEY_ID --secret_key $CALLER_SECRET_ACCESS_KEY --security_token $CALLER_SESSION_TOKEN $TEST_URL

