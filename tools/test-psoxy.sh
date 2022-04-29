#!/bin/bash
ECHO=false
SANITIZATION_HEADER="X-Psoxy-Skip-Sanitizer: false"

function show_help() {
    echo "-g endpoint is GCP"
    echo "-a endpoint is AWS"
    echo "-r AWS role to impersonate"
    echo "-u URL to call"
    echo "-v verbose"
    echo "-o omit sanitization rules, only works if function deployed in development mode"
    echo "-h show this help"
    exit 0
}

while getopts gahvor:u: flag
do
    case "${flag}" in
        g) GCP=true;;
        a) AWS=true;;
        r) ROLE_ARN=${OPTARG};;
        v) ECHO=true;;
        u) TEST_URL=${OPTARG};;
        o) SANITIZATION_HEADER="X-Psoxy-Skip-Sanitizer: true";;
        h) show_help;;
    esac
done

function log() {
    if [ "$ECHO" = true ]; then
      echo $1
    fi
}

if [ "$AWS" = true ];
then
  log "Assuming role $ROLE_ARN"
  aws sts assume-role --role-arn $ROLE_ARN --duration 900 --role-session-name lambda_test --output json > token.json
  export CALLER_ACCESS_KEY_ID=`cat token.json| jq -r '.Credentials.AccessKeyId'`
  export CALLER_SECRET_ACCESS_KEY=`cat token.json| jq -r '.Credentials.SecretAccessKey'`
  export CALLER_SESSION_TOKEN=`cat token.json| jq -r '.Credentials.SessionToken'`
  rm token.json
  log "Calling proxy..."
  log "Request: $TEST_URL"
  log "Waiting Response:"
  awscurl -v -H "$SANITIZATION_HEADER" --service execute-api --access_key $CALLER_ACCESS_KEY_ID --secret_key $CALLER_SECRET_ACCESS_KEY --security_token $CALLER_SESSION_TOKEN $TEST_URL
  # Remove env variables
  unset CALLER_ACCESS_KEY_ID CALLER_SECRET_ACCESS_KEY CALLER_SESSION_TOKEN
fi

if [ "$GCP" = true ];
then
  log "Getting token"
  CURL_AUTH_HEADER="Authorization: Bearer $(gcloud auth print-identity-token)"
  log "Calling proxy..."
  log "Request: $TEST_URL"
  log "Waiting Response:"
  curl -X GET -H "$CURL_AUTH_HEADER" -H "$SANITIZATION_HEADER" $TEST_URL
fi
