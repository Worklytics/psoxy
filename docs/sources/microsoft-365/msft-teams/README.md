# Microsoft Teams

Connect Microsoft Teams data to Worklytics, enabling communication analysis and general collaboration
insights based on collaboration via Microsoft Teams. Includes user enumeration to support fetching
mailboxes from each account; and group enumeration to expand emails via mailing list (groups).

Please review the [Microsoft 365 README](../README.md) for general information applicable to
all Microsoft 365 connectors.

## Required Scopes
- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`Team.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#teamreadbasicall)
- [`Channel.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelreadbasicall)
- [`Chat.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#chatreadbasicall)
- [`ChannelMessage.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelmessagereadall)
- [`CallRecords.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#callrecordsreadall)
- [`OnlineMeetings.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#onlinemeetingsreadall)

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

### Online Meetings support

Besides of having `OnlineMeetings.Read.All` and `OnlineMeetingArtifact.Read.All` scope defined in
the application, you need to allow a new role and a policy on the application created for reading
OnlineMeetings. You will need Powershell for this.

Please follow the steps below:

1. Ensure the user you are going to use for running the commands has the "Teams Administrator" role.
   You can add the role in the
   [Microsoft 365 Admin Center](https://learn.microsoft.com/en-us/microsoft-365/admin/add-users/assign-admin-roles?view=o365-worldwide#assign-a-user-to-an-admin-role-from-active-users)

**NOTE**: It can be assigned through Entra Id portal in Azure portal OR in Entra Admin center
https://admin.microsoft.com/AdminPortal/Home. It is possible that even login with an admin account
in Entra Admin Center the Teams role is not available to assign to any user; if so, please do it
through Azure Portal (Entra Id -> Users -> Assign roles)

2. Install
   [PowerShell Teams](https://learn.microsoft.com/en-us/microsoftteams/teams-powershell-install)
   module.
3. Run the following commands in Powershell terminal:

```shell
Connect-MicrosoftTeams
```

And use the user with the "Teams Administrator" for login it.

4. Follow steps on
   [Configure application access to online meetings or virtual events](https://learn.microsoft.com/en-us/graph/cloud-communication-online-meeting-application-access-policy):

- Add a policy for the application created for the connector, providing its `application id`
- Grant the policy to the whole tenant (NOT to any specific application or user)

**Issues**:

- If you receive "access denied" is because no admin role for Teams has been detected. Please close
  and reopen the Powershell terminal after assigning the role.
- Commands have been tested over a Powershell (7.4.0) terminal in Windows, installed from Microsoft
  Store and with Teams Module (5.8.0). It might not work on a different environment

## Example Data

| API Endpoint                                                                  | Example Response                                                                                                                           | Sanitized Example Response                                                                                                                   |
|-------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `/v1.0/teams`                                                                 | [original/Teams_v1.0.json](example-api-responses/original/Teams_v1.0.json)                                                                 | [sanitized/Teams_v1.0.json](example-api-responses/sanitized/Teams_v1.0.json)                                                                 |
| `/v1.0/teams/{teamId}/allChannels`                                            | [original/Teams_allChannels_v1.0.json](example-api-responses/original/Teams_allChannels_v1.0.json)                                         | [sanitized/Teams_allChannels_v1.0.json](example-api-responses/sanitized/Teams_allChannels_v1.0.json)                                         |
| `/v1.0/teams/{teamId}/channels/{channelId}/messages`                          | [original/Teams_channels_messages_v1.0.json](example-api-responses/original/Teams_channels_messages_v1.0.json)                             | [sanitized/Teams_channels_messages_v1.0.json](example-api-responses/sanitized/Teams_channels_messages_v1.0.json)                             |
| `/v1.0/users/{userId}/chats`                                                  | [original/Chats_messages_v1.0.json](example-api-responses/original/Chats_messages_v1.0.json)                                               | [sanitized/Chats_messages_v1.0.json](example-api-responses/sanitized/Chats_messages_v1.0.json)                                               |
| `/v1.0/users/{userId}/onlineMeetings`                                         | [original/Users_onlineMeetings_v1.0.json](example-api-responses/original/Users_onlineMeetings_v1.0.json)                                   | [sanitized/Users_onlineMeetings_v1.0.json](example-api-responses/sanitized/Users_onlineMeetings_v1.0.json)                                   |
| `/v1.0/users/{userId}/onlineMeetings/{meetingId}/attendanceReport/{reportId}` | [original/Users_onlineMeetings_attendanceReport_v1.0.json](example-api-responses/original/Users_onlineMeetings_attendanceReport_v1.0.json) | [sanitized/Users_onlineMeetings_attendanceReport_v1.0.json](example-api-responses/sanitized/Users_onlineMeetings_attendanceReport_v1.0.json) |
|
See more examples in the `docs/sources/microsoft-365/msft-teams/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

**NOTE for pseudonymizing app ids**

In case of `pseudonymize_app_ids` is set to `true`, the `userId` and `chatId` fields will be tokenized. In such case and if you want
to populate example variables like `example_msft_user_guid` or `example_msft_chat_guid` in the example responses, you will need first to
get a list of user and use the `id` in the variable. Using a plain user id without tokenization might not work on endpoints that require
a tokenized user id.

## Example Rules

- [Example Rules](msft-teams.yaml)
- [Example Rules: no User IDs](msft-teams_no-userIds.yaml)