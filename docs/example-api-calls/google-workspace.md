# API Call Examples

Example curl commands that you can use to validate proxy behavior against various source APIs.

To use, ensure you've set env variables on your machine:
```shell
export PSOXY_HOST={{YOUR_GCP_REGION}}-{{YOUR_PROJECT_ID}}
export PSOXY_USER_TO_IMPERSONATE=you@acme.com
```

If any call appears to fail, repeat it without the pipe to jq (eg, remove `| jq ...` portion from
the end of the command).

## Calendar

### Calendar
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gcal/calendar/v3/calendars/primary \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Settings
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gcal/calendar/v3/users/me/settings \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Events
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gcal/calendar/v3/calendars/primary/events \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Event
```shell
export CALENDAR_EVENT_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gcal/calendar/v3/calendars/primary/events \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gcal/calendar/v3/calendars/primary/events/`echo $CALENDAR_EVENT_ID` \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

## Directory

### Domains
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/customer/my_customer/domains \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Groups
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/groups\?customer=my_customer \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Group
```shell
export GOOGLE_GROUP_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/groups\?customer=my_customer \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.groups[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/groups/`echo $GOOGLE_GROUP_ID` \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Group Members
```shell
export GOOGLE_GROUP_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/groups\?customer=my_customer \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.groups[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/groups/`echo $GOOGLE_GROUP_ID`/members \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Users
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/users\?customer=my_customer \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

```shell
export GOOGLE_USER_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/users\?customer=my_customer \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.users[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/users/`echo $GOOGLE_USER_ID` \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Org Units
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/customer/my_customer/orgunits \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Roles
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/customer/my_customer/roles \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdirectory/admin/directory/v1/customer/my_customer/roleassignments \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

## Drive

### Files
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v3/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### File
```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/`echo $DRIVE_FILE_ID` \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v3/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.files[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v3/files/`echo $DRIVE_FILE_ID`\?fields=\* \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### File Revisions
YMMV, as file at index `0` must actually be a type that supports revisions for this to return
anything. You can play with that value until you find something that does.
```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/`echo $DRIVE_FILE_ID`/revisions \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v3/files\?pageSize=2\&fields=\* \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.files[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v3/files/`echo $DRIVE_FILE_ID`/revisions\?pageSize=2\&fields=\* \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Permissions

```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/`echo $DRIVE_FILE_ID`/permissions \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v3/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.files[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v3/files/`echo $DRIVE_FILE_ID`/permissions\?fields=\* \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Comments
YMMV, as file at index `0` must actually be a type that has comments for this to return
anything. You can play with that value until you find something that does.

**NOTE probably blocked by OAuth metadata only scope!!**
```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/`echo $DRIVE_FILE_ID`/comments \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```


```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v3/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.files[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v3/files/`echo $DRIVE_FILE_ID`/comments\?fields=\* \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```


### Comment

**NOTE probably blocked by OAuth metadata only scope!!**

```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

export DRIVE_COMMENT_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/\`echo $DRIVE_FILE_ID\`/comments  \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/`echo $DRIVE_FILE_ID`/comments/`echo $DRIVE_COMMENT_ID` \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Replies
**NOTE probably blocked by OAuth metadata only scope!!**

YMMV, as above, play with the index values until you find a file with comments, and a comment that
has replies.
```shell
export DRIVE_FILE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

export DRIVE_COMMENT_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/\`echo $DRIVE_FILE_ID\`/comments  \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.items[0].id'`

curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gdrive/drive/v2/files/`echo $DRIVE_FILE_ID`/comments/`echo $DRIVE_COMMENT_ID`/replies \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

## GMail

### Messages
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gmail/gmail/v1/users/me/messages \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

### Message
```shell
export GMAIL_MESSAGE_ID=`
curl -X GET \
https://\`echo $PSOXY_HOST\`.cloudfunctions.net/psoxy-gmail/gmail/v1/users/me/messages \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq -r '.messages[0].id'`
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gmail/gmail/v1/users/me/messages/`echo $GMAIL_MESSAGE_ID`\?format=metadata \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

## Google Chat

NOTE: limited to 10 results, to keep it readable.
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-google-chat/admin/reports/v1/activity/users/all/applications/chat\?maxResults=10 \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

## Google Meet

NOTE: limited to 10 results, to keep it readable.
```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-google-meet/admin/reports/v1/activity/users/all/applications/meet\?maxResults=10 \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```
