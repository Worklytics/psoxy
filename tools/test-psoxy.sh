#!/bin/bash
ECHO=false

function show_help() {
    echo "Basic call: ./test-psoxy.sh [-g|-a -r ROLE] [-i] -u https://url-to-proxy-function/path-to-api"
    echo "-g endpoint is GCP"
    echo "-a endpoint is AWS"
    echo "-r AWS role to impersonate"
    echo "-i user to impersonate, needed for certain connectors"
    echo "-u URL to call"
    echo "-v verbose"
    echo "-s skip sanitization rules, only works if function deployed in development mode"
    echo "-h show this help"
    exit 0
}

while getopts gahvsr:u:i: flag
do
    case "${flag}" in
        g) GCP=true;;
        a) AWS=true;;
        r) ROLE_ARN=${OPTARG};;
        v) ECHO=true; curlparams+=("-v");;
        u) TEST_URL=${OPTARG};;
        s) curlparams+=("-HX-Psoxy-Skip-Sanitizer: true");;
        i) curlparams+=("-HX-Psoxy-User-To-Impersonate: ${OPTARG}");;
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
  awscurl "${curlparams[@]}" --service execute-api --access_key $CALLER_ACCESS_KEY_ID --secret_key $CALLER_SECRET_ACCESS_KEY --security_token $CALLER_SESSION_TOKEN $TEST_URL
  # Remove env variables
  unset CALLER_ACCESS_KEY_ID CALLER_SECRET_ACCESS_KEY CALLER_SESSION_TOKEN
fi

if [ "$GCP" = true ];
then
  log "Getting token"
  curlparams+=("-HAuthorization: Bearer $(gcloud auth print-identity-token)")
  log "Calling proxy..."
  log "Request: $TEST_URL"
  log "Waiting Response:"
  curl "${curlparams[@]}" $TEST_URL
fi
