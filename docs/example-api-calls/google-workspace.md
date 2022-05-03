# API Call Examples for Google Workspace

Example commands that you can use to validate proxy behavior against the Google Workspace APIs.
Follow the steps and change the values to match your configuration when needed.

To use, ensure you've set env variables on your machine:
```shell
# no slash at the end, and no function or lambda, just the base url of the deployment
export PSOXY_BASE=https://YOUR_PSOXY_HOST
export PSOXY_USER_TO_IMPERSONATE=you@acme.com
```

For GCP, use
```shell
export OPTIONS="-g -i $PSOXY_USER_TO_IMPERSONATE"
```

For AWS, change the role to impersonate with one with sufficient permissions to call the proxy
```shell
export AWS_ROLE_ARN="arn:aws:iam::PROJECT_ID:role/ROLE_NAME"
export OPTIONS="-a -r $AWS_ROLE_ARN -i $PSOXY_USER_TO_IMPERSONATE"
```

If any call appears to fail, repeat it without the pipe to jq (eg, remove `| jq ...` portion from
the end of the command) and "-v" flag.

## Calendar

### Calendar
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gcal/calendar/v3/calendars/primary | jq .
```

### Settings
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gcal/calendar/v3/users/me/settings | jq .
```

### Events
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gcal/calendar/v3/calendars/primary/events | jq .
```

### Event
```shell
export CALENDAR_EVENT_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gcal/calendar/v3/calendars/primary/events | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gcal/calendar/v3/calendars/primary/events/$CALENDAR_EVENT_ID | jq .
```

## Directory

### Domains
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/customer/my_customer/domains | jq .
```

### Groups
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/groups\?customer=my_customer | jq .
```

### Group
```shell
export GOOGLE_GROUP_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/groups\?customer=my_customer | jq -r '.groups[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/groups/$GOOGLE_GROUP_ID | jq .
```

### Group Members
```shell
export GOOGLE_GROUP_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/groups\?customer=my_customer | jq -r '.groups[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/groups/$GOOGLE_GROUP_ID/members | jq .
```

### Users
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/users\?customer=my_customer | jq .
```

```shell
export GOOGLE_USER_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/users\?customer=my_customer | jq -r '.users[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/users/$GOOGLE_USER_ID | jq .
```

Thumbnail (expect have its contents redacted)
```shell
export GOOGLE_USER_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/users\?customer=my_customer | jq -r '.users[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/users/$GOOGLE_USER_ID/photos/thumbnail | jq .
```

### Org Units
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/customer/my_customer/orgunits | jq .
```

### Roles
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/customer/my_customer/roles | jq .
```

```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdirectory/admin/directory/v1/customer/my_customer/roleassignments | jq .
```

## Drive

### Files
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq .
```

```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files | jq .
```

### File
```shell
export DRIVE_FILE_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/$DRIVE_FILE_ID | jq .
```

```shell
export DRIVE_FILE_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files | jq -r '.files[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files/$DRIVE_FILE_ID\?fields=\* | jq .
```

### File Revisions
YMMV, as file at index `0` must actually be a type that supports revisions for this to return
anything. You can play with that value until you find something that does.
```shell
export DRIVE_FILE_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/$DRIVE_FILE_ID/revisions | jq .
```

```shell
export DRIVE_FILE_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files\?pageSize=2\&fields=\* | jq -r '.files[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files/$DRIVE_FILE_ID/revisions\?pageSize=2\&fields=\* | jq .
```

### Permissions

```shell
export DRIVE_FILE_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/$DRIVE_FILE_ID/permissions | jq .
```

```shell
export DRIVE_FILE_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files | jq -r '.files[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files/$DRIVE_FILE_ID/permissions\?fields=\* | jq .
```

### Comments
YMMV, as file at index `0` must actually be a type that has comments for this to return
anything. You can play with that value until you find something that does.

**NOTE probably blocked by OAuth metadata only scope!!**
```shell
export DRIVE_FILE_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/$DRIVE_FILE_ID/comments | jq .
```


```shell
export DRIVE_FILE_ID=`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files | jq -r '.files[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v3/files/$DRIVE_FILE_ID/comments\?fields=\* | jq .
```


### Comment

**NOTE probably blocked by OAuth metadata only scope!!**

```shell
export DRIVE_FILE_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq -r '.items[0].id'`

export DRIVE_COMMENT_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/\`echo $DRIVE_FILE_ID\`/comments  | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/$DRIVE_FILE_ID/comments/`echo $DRIVE_COMMENT_ID` | jq .
```

### Replies
**NOTE probably blocked by OAuth metadata only scope!!**

YMMV, as above, play with the index values until you find a file with comments, and a comment that
has replies.
```shell
export DRIVE_FILE_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files | jq -r '.items[0].id'`

export DRIVE_COMMENT_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/\`echo $DRIVE_FILE_ID\`/comments  | jq -r '.items[0].id'`

./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gdrive/drive/v2/files/$DRIVE_FILE_ID/comments/`echo $DRIVE_COMMENT_ID`/replies | jq .
```

## GMail

### Messages
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gmail/gmail/v1/users/me/messages | jq .
```

### Message
```shell
export GMAIL_MESSAGE_ID=`./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gmail/gmail/v1/users/me/messages | jq -r '.messages[0].id'`
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-gmail/gmail/v1/users/me/messages/`echo $GMAIL_MESSAGE_ID`\?format=metadata | jq .
```

## Google Chat

NOTE: limited to 10 results, to keep it readable.
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-google-chat/admin/reports/v1/activity/users/all/applications/chat\?maxResults=10 | jq .
```

## Google Meet

NOTE: limited to 10 results, to keep it readable.
```shell
./test-psoxy.sh $OPTIONS -u $PSOXY_BASE/psoxy-google-meet/admin/reports/v1/activity/users/all/applications/meet\?maxResults=10 | jq .
```
