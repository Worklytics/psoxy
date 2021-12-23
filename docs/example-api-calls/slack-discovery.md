# API Call Examples

Example `curl` commands that you can use to validate proxy behavior against various source APIs.

To use, ensure you've set env variables on your machine:

```shell
export PSOXY_HOST={{YOUR_GCP_REGION}}-{{YOUR_PROJECT_ID}}
export SLACK_DISCOVERY_BASE_URL="https://$PSOXY_HOST.cloudfunctions.net/psoxy-slack-discovery-api"
export CURL_AUTH_HEADER="Authorization: Bearer $(gcloud auth print-identity-token)"
```

If any call appears to fail, repeat it without the pipe to jq (eg, remove `| jq ...` portion from
the end of the command).

### Read workspaces
```shell
curl -X GET $SLACK_DISCOVERY_BASE_URL/api/discovery.enterprise.info -H "$CURL_AUTH_HEADER" | jq .
```

### Read Users in Grid
```shell
curl -X GET $SLACK_DISCOVERY_BASE_URL/api/discovery.users.list?include_deleted=true -H "$CURL_AUTH_HEADER" | jq .
```

### Read Conversations in Workspace (any kind, public and private)

Next calls operate on a workspace so get an arbitrary workspace in variable
```shell
export WORKSPACE_ID=`curl -X GET $SLACK_DISCOVERY_BASE_URL/api/discovery.enterprise.info?limit=1 -H "$CURL_AUTH_HEADER" | jq -r '.enterprise.teams[0].id'`
```

```shell
curl -X GET $SLACK_DISCOVERY_BASE_URL/api/discovery.conversations.list?team=$WORKSPACE_ID\&limit=10 -H "$CURL_AUTH_HEADER" | jq .
```

### Read Messages in Workspace Channel

Next call operate on channels belonging to a workspace. Get an arbitrary channel in variable
```shell
export CHANNEL_ID=`curl -X GET $SLACK_DISCOVERY_BASE_URL/api/discovery.conversations.list?team=$WORKSPACE_ID\&limit=1 -H "$CURL_AUTH_HEADER" | jq -r '.channels[0].id'`
```

```shell
curl -X GET $SLACK_DISCOVERY_BASE_URL/api/discovery.conversations.history?team=$WORKSPACE_ID\&channel=$CHANNEL_ID\&limit=10 -H "$CURL_AUTH_HEADER" | jq .
```
