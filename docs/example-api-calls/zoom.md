# API Call Examples for Zoom

Example commands that you can use to validate proxy behavior against the Zoom APIs.
Follow the steps and change the values to match your configuration when needed.

```shell
export PROXY_ZOOM_URL=https://YOUR_PSOXY_HOST/psoxy-zoom
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

### List users
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/users | jq .
```
Now pull out a user id, and add it as env variable. Next calls are bound to a single user.
```shell
export ZOOM_USER_ID=$(./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/users | jq -r .users[0].id | jq -r .original)
```

### List user meetings
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/users/$ZOOM_USER_ID/meetings
```

## Meetings
First pull out a meeting id, and add it as env variable

```shell
export MEETING_ID=$(./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/users/$ZOOM_USER_ID/meetings | jq -r .meetings[0].id)
```

### List past meeting details
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/past_meetings/$MEETING_ID
```

### List past meeting instances
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/past_meetings/$MEETING_ID/instances
```

### List past meeting participants
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/past_meetings/$MEETING_ID/participants
```

### Get meeting details
```shell
./test-psoxy.sh $OPTIONS -u $PROXY_ZOOM_URL/v2/meetings/$MEETING_ID
```
