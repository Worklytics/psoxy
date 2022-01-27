# API Call Examples

Example test commands that you can use to validate proxy behavior against various source APIs.

## Directory

Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or UserPrincipalName (often your email address).
```
/v1.0/users
/v1.0/users/me
/v1.0/groups
/v1.0/groups/{groupId}
/v1.0/groups/{groupId}/members?$count=true
```

## Calendar
Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or UserPrincipalName (often your email address).
```
/v1.0/users/me/events
/v1.0/users/me/calendars
/v1.0/users/me/events/{eventId}
/v1.0/users/me/mailboxSettings
```

## Mail
Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or UserPrincipalName (often your email address).

NOTE: `beta` is used, as Worklytics relies on 'metadata-only' oauth scope `Messages.ReadBasic`,
which is only supported by that API version.

```
/beta/users/me/mailFolders/SentItems/messages
/beta/users/me/messages/{messageId}
/beta/users/me/mailboxSettings
```




