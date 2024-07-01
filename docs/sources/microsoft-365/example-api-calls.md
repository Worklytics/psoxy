# API Call Examples

Example test commands that you can use to validate proxy behavior against various source APIs.

## Directory

Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or
UserPrincipalName (often your email address).

```
/v1.0/users
/v1.0/users/me
/v1.0/groups
/v1.0/groups/{groupId}
/v1.0/groups/{groupId}/members?$count=true
```

## Calendar

Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or
UserPrincipalName (often your email address).

```
/v1.0/users/me/events
/v1.0/users/me/calendars
/v1.0/users/me/events/{eventId}
/v1.0/users/me/mailboxSettings
```

## Mail

Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or
UserPrincipalName (often your email address).

```
/v1.0/users/me/mailFolders/SentItems/messages
/v1.0/users/me/messages/{messageId}
/v1.0/users/me/mailboxSettings
```