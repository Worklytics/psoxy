# API Call Examples for Google Workspace

Example commands (\*) that you can use to validate proxy behavior against the Google Workspace APIs.
Follow the steps and change the values to match your configuration when needed.

You can use the `-i` flag to impersonate the desired user identity option when running the testing
tool. Example:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary -i you@acme.com
```

For AWS, change the role to assume with one with sufficient permissions to call the proxy (`-r`
flag). Example:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If any call appears to fail, repeat it using the `-v` flag.

(\*) All commands assume that you are at the root path of the Psoxy project.

### Calendar

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary
```

### Settings

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/users/me/settings
```

### Events

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary/events
```

### Event

1. Get the calendar event ID (accessor path in response `.items[0].id`):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary/events
```

2. Get event information (replace `calendar_event_id` with the corresponding value):

```
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gcal/calendar/v3/calendars/primary/events/[calendar_event_id]
```

## Directory

### Domains

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/customer/my_customer/domains
```

### Groups

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/groups?customer=my_customer
```

### Group

1. Get the group ID (accessor path in response `.groups[0].id`):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/groups?customer=my_customer
```

2. Get group information (replace `google_group_id` with the corresponding value):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/groups/[google_group_id]
```

### Group Members

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/groups/[google_group_id]/members
```

### Users

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/users?customer=my_customer
```

1. Get the user ID (accessor path in response `.users[0].id`):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/users?customer=my_customer
```

2. Get user information (replace [google_user_id] with the corresponding value):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/users/[google_user_id]
```

3. Thumbnail (expect have its contents redacted; replace [google_user_id] with the corresponding
   value):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/users/[google_user_id]/photos/thumbnail
```

### Roles

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/customer/my_customer/roles
```

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdirectory/admin/directory/v1/customer/my_customer/roleassignments
```

## Drive

### Files

API v2

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files
```

API v3 (\*)

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v3/files
```

(\*) Notice that only the "version" part of the URL changes, and all subsequent calls should work
for `v2` and also `v3`.

### File

1. Get the file ID (accessor path in response `.files[0].id`:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files
```

2. Get file details (replace [drive_file_id] with the corresponding value):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]?fields=*
```

### File Revisions

YMMV, as file at index `0` must actually be a type that supports revisions for this to return
anything. You can play with different file IDs until you find something that does.

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/revisions
```

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/revisions?pageSize=2&fields=*
```

### Permissions

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/permissions
```

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/permissions?fields=*
```

### Comments

YMMV, as file at index `0` must actually be a type that has comments for this to return anything.
You can play with different file IDs until you find something that does.

**NOTE probably blocked by OAuth metadata only scope!!**

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/comments
```

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/comments?fields=*
```

### Comment

**NOTE probably blocked by OAuth metadata only scope!!**

1. Get file comment ID (accessor path in response `.items[0].id`):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/comments
```

2. Get file comment details (replace `file_comment_id` with the corresponding value):

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/comments/[file_comment_id]
```

### Replies

**NOTE probably blocked by OAuth metadata only scope!!**

YMMV, as above, play with the file comment ID value until you find a file with comments, and a
comment that has replies.

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gdrive/drive/v2/files/[drive_file_id]/comments/[file_comment_id]/replies
```

## GMail

### Messages

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gmail/gmail/v1/users/me/messages
```

### Message

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-gmail/gmail/v1/users/me/messages/[gmail_message_id]?format=metadata
```

## Google Chat

NOTE: limited to 10 results, to keep it readable.

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-google-chat/admin/reports/v1/activity/users/all/applications/chat?maxResults=10
```

## Google Meet

NOTE: limited to 10 results, to keep it readable.

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/psoxy-google-chat/admin/reports/v1/activity/users/all/applications/meet?maxResults=10
```
