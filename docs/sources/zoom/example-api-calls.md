# API Call Examples for Zoom

Example commands (\*) that you can use to validate proxy behavior against the Zoom APIs. Follow the
steps and change the values to match your configuration when needed.

For AWS, change the role to assume with one with sufficient permissions to call the proxy (`-r`
flag). Example:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If any call appears to fail, repeat it using the `-v` flag.

(\*) All commands assume that you are at the root path of the Psoxy project.

### List users

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users
```

Now pull out a user id (`[zoom_user_id]`, accessor path in response `.users[0].id`). Next call is
bound to a single user:

### List user meetings

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users/[zoom_user_id]/meetings
```

## Meetings

First pull out a meeting id (`[zoom_meeting_id]`, accessor path in response `.meetings[0].id`):

### List past meeting details

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_id]
```

### List past meeting instances

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_id]/instances
```

### List past meeting participants

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_id]/participants
```

### Get meeting details

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/meetings/[zoom_meeting_id]
```
