# API Call Examples for Zoom

Example commands (*) that you can use to validate proxy behavior against the Zoom APIs.
Follow the steps and change the values to match your configuration when needed.

For GCP, you can use the `-i` flag to impersonate the desired user identity option when running the testing tool. Example:

```shell
node tools/test-psoxy.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary -i you@acme.com
```

For AWS, change the role to impersonate with one with sufficient permissions to call the proxy (`-r` flag). Example:

```shell
node tools/test-psoxy.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If any call appears to fail, repeat it using the `-v` flag.

(*) All commands assume that you are at the root path of the Psoxy project.

### List users
```shell
node tools/test-psoxy.js -u [your_psoxy_url]/v2/users
```
Now pull out a user id (`[zoom_user_id]`). Next call is bound to a single user:
### List user meetings
```shell
node tools/test-psoxy.js -u [your_psoxy_url]/v2/users/[zoom_user_id]/meetings
```

## Meetings
First pull out a meeting id (`[zoom_meeting_id]`):
### List past meeting details
```shell
node tools/test-psoxy.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_id]
```

### List past meeting instances
```shell
node tools/test-psoxy.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_id]/instances
```

### List past meeting participants
```shell
node tools/test-psoxy.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_id]/participants
```

### Get meeting details
```shell
node tools/test-psoxy.js -u [your_psoxy_url]/v2/meetings/[zoom_meeting_id]
```
