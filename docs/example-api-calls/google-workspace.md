# API Call Examples

Example curl commands that you can use to validate proxy behavior against various source APIs.

To use, ensure you've set env valariables
```shell
export PSOXY_HOST={{YOUR_GCP_REGION}}-{{YOUR_PROJECT_ID}}
export PSOXY_USER_TO_IMPERSONATE=you@acme.com
```

## Calendar

### Settings
```shell
TBD
```

### Events
```shell
TBD
```

### Event
```shell
TBD
```

## Directory

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
TBD
```

### Org Units
```shell
TBD
```

### Roles
```shell
admin/directory/v1/customer/{customer}/roleassignments


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

```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-google-chat/admin/reports/v1/activity/users/all/applications/chat \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)" | jq .
```

