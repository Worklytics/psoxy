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

### Get a meeting summary

`GET /v2/meetings/{meetingId}/meeting_summary` only returns data for a **past meeting instance** that generated a Zoom AI Companion meeting summary. `{meetingId}` must be that instance's UUID — not the numeric meeting number from `GET /v2/users/{userId}/meetings`, which lists scheduled/upcoming meetings and typically yields Zoom error `300` (`Invalid meeting id`).

Use `tools/psoxy-test/find-zoom-meeting-summary.js` to walk users → past meetings → instances until `has_meeting_summary` is true, then call the summary endpoint. It lives next to `cli-call.js` and takes the Zoom function **base URL** (no API path) plus the same flags as `test-zoom.sh`:

```shell
# GCP (including external ALB)
node tools/psoxy-test/find-zoom-meeting-summary.js -u [your_psoxy_url] -f gcp --allow-insecure-tls

# AWS
node tools/psoxy-test/find-zoom-meeting-summary.js -u [your_psoxy_url] -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

Once you have an instance UUID (`[zoom_meeting_uuid]`), you can also call:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/[zoom_meeting_uuid]
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/meetings/[zoom_meeting_uuid]/meeting_summary
```

If the UUID starts with `/` or contains `//`, [double-encode](https://developers.zoom.us/docs/api/rest/using-zoom-apis/#meeting-id-and-uuid) it in the path. Smart Recording summaries in the Zoom UI are a different feature; this endpoint only returns AI Companion **meeting** summaries.
