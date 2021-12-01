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
TBD
```

### File
```shell
TBD
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
