# API Call Examples for Slack Discovery

Example commands that you can use to validate proxy behavior against the Slack Discovery APIs.
Follow the steps and change the values to match your configuration when needed.

```shell
export PROXY_SLACK_URL=https://YOUR_PSOXY_HOST/psoxy-slack-discovery-api
```

For GCP, use
```shell
export OPTIONS="-g"
```

For AWS, change the role to impersonate with one with sufficient permissions to call the proxy
```shell
export AWS_ROLE_ARN="arn:aws:iam::PROJECT_ID:role/ROLE_NAME"
export OPTIONS="-a -r $AWS_ROLE_ARN"
```

If any call appears to fail, repeat it without the pipe to jq (eg, remove `| jq ...` portion from
the end of the command) and "-v" flag.

### Read workspaces
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.enterprise.info | jq .
```

### Read Users in Grid
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.users.list?include_deleted=true | jq .
```

### Read Conversations in Workspace (any kind, public and private)

Next calls operate on a workspace so get an arbitrary workspace in variable
```shell
export WORKSPACE_ID=`./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.enterprise.info?limit=1 | jq -r '.enterprise.teams[0].id'`
```

```shell
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.list?team=$WORKSPACE_ID\&limit=10 | jq .
```

### Read Messages in Workspace Channel

Next call operate on channels belonging to a workspace. Get an arbitrary channel in variable
```shell
# get a workspace channel
export CHANNEL_ID=`./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.list?team=$WORKSPACE_ID\&limit=10 | jq -r '.channels[0].id'`
# or for a DM (no workspace)
export CHANNEL_ID=`./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.list?limit=10 | jq -r '.channels[0].id'`
```

```shell
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.history?team=$WORKSPACE_ID\&channel=$CHANNEL_ID\&limit=10 | jq .
# omit the workspace id if channel is a DM
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.history?channel=$CHANNEL_ID\&limit=10 | jq .
```

### Workspace Channel Info
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.info?team=$WORKSPACE_ID\&channel=$CHANNEL_ID\&limit=1 | jq .
# omit the workspace id if channel is a DM
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.info?channel=$CHANNEL_ID\&limit=1 | jq .
```

### Recent Workspace Conversations
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_SLACK_URL/api/discovery.conversations.recent | jq .
```
