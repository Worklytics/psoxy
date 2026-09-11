# Microsoft Teams

**Connector ID:** `msft-teams`

**Availability:** GA

Connect Microsoft Teams data to Worklytics, enabling communication analysis and general collaboration insights based on collaboration via Microsoft Teams. Includes user enumeration to support fetching mailboxes from each account; and group enumeration to expand emails via mailing list (groups).

Please review the [Microsoft 365 README](../README.md) for general information applicable to all Microsoft 365 connectors.

## Required Scopes

- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`Team.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#teamreadbasicall)
- [`Channel.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelreadbasicall)
- [`Chat.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#chatreadall)
- [`ChannelMessage.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelmessagereadall)
- [`CallRecords.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#callrecordsreadall)
- [`OnlineMeetings.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#onlinemeetingsreadall)
- [`OnlineMeetingArtifact.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#onlinemeetingartifactreadall)

You must also [Configure Access to Online Meetings](#configure-access-to-online-meetings) for your application.

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

### Configure Access to Online Meetings

Besides having `OnlineMeetings.Read.All` and `OnlineMeetingArtifact.Read.All` scopes defined in the application, you need to allow a new role and a policy on the application created for reading online meetings. You will need [PowerShell](https://learn.microsoft.com/en-us/powershell/scripting/install/install-powershell) for this.

Please follow the steps below:

1. Ensure the user you are going to use for running the commands has the "Teams Administrator" role. You can add the role in the [Microsoft 365 Admin Center](https://learn.microsoft.com/en-us/microsoft-365/admin/add-users/assign-admin-roles?view=o365-worldwide#assign-a-user-to-an-admin-role-from-active-users)

**NOTE**: It can be assigned through the Entra ID portal in Azure Portal or in the [Microsoft 365 Admin Center](https://admin.microsoft.com/AdminPortal/Home). Even when logged in with an admin account in Entra admin center, the Teams role may not be available to assign; if so, assign it through Azure Portal (Entra ID → Users → Assign roles).

2. Install the [PowerShell Teams](https://learn.microsoft.com/en-us/microsoftteams/teams-powershell-install) module.
3. Run the following command in a PowerShell terminal:

```shell
Connect-MicrosoftTeams
```

Sign in with the user that has the "Teams Administrator" role.

4. Follow the steps in [Configure application access to online meetings or virtual events](https://learn.microsoft.com/en-us/graph/cloud-communication-online-meeting-application-access-policy):

- Add a policy for the application created for the connector, providing its `application id`
- Grant the policy to the whole tenant (NOT to any specific application or user)

**Issues**:

- If you receive "access denied", no Teams admin role has been detected. Close and reopen the PowerShell terminal after assigning the role.
- Commands have been tested on PowerShell 7.4.0 on Windows, installed from the Microsoft Store, with Teams module 5.8.0. They might not work in a different environment.

## Example Data

| API Endpoint                                                                   | Example Response                                                                                                                             | Sanitized Example Response                                                                                                                     |
|---------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `/v1.0/teams`                                                                  | [original/Teams_v1.0.json](example-api-responses/original/Teams_v1.0.json)                                                                   | [sanitized/Teams_v1.0.json](example-api-responses/sanitized/Teams_v1.0.json)                                                                   |
| `/v1.0/teams/{teamId}/allChannels`                                             | [original/Teams_allChannels_v1.0.json](example-api-responses/original/Teams_allChannels_v1.0.json)                                           | [sanitized/Teams_allChannels_v1.0.json](example-api-responses/sanitized/Teams_allChannels_v1.0.json)                                           |
| `/v1.0/teams/{teamId}/channels/{channelId}/messages`                           | [original/Teams_channels_messages_v1.0.json](example-api-responses/original/Teams_channels_messages_v1.0.json)                               | [sanitized/Teams_channels_messages_v1.0.json](example-api-responses/sanitized/Teams_channels_messages_v1.0.json)                               |
| `/v1.0/teams/{teamId}/channels/{channelId}/messages/delta`                     | [original/Teams_channels_messages_delta_v1.0.json](example-api-responses/original/Teams_channels_messages_delta_v1.0.json)                   | [sanitized/Teams_channels_messages_delta_v1.0.json](example-api-responses/sanitized/Teams_channels_messages_delta_v1.0.json)                   |
| `/v1.0/users/{userId}/chats`                                                   | [original/Users_chats_v1.0.json](example-api-responses/original/Users_chats_v1.0.json)                                                       | [sanitized/Users_chats_v1.0.json](example-api-responses/sanitized/Users_chats_v1.0.json)                                                       |
| `/v1.0/chats/{chatId}/messages`                                                | [original/Chats_messages_v1.0.json](example-api-responses/original/Chats_messages_v1.0.json)                                                 | [sanitized/Chats_messages_v1.0.json](example-api-responses/sanitized/Chats_messages_v1.0.json)                                                 |
| `/v1.0/communications/calls/{callId}`                                          | [original/Communications_calls_v1.0.json](example-api-responses/original/Communications_calls_v1.0.json)                                     | [sanitized/Communications_calls_v1.0.json](example-api-responses/sanitized/Communications_calls_v1.0.json)                                     |
| `/v1.0/communications/callRecords`                                             | [original/Communications_callRecords_v1.0.json](example-api-responses/original/Communications_callRecords_v1.0.json)                         | [sanitized/Communications_callRecords_v1.0.json](example-api-responses/sanitized/Communications_callRecords_v1.0.json)                         |
| `/v1.0/communications/callRecords/{callRecordId}`                              | [original/Communications_callRecord_v1.0.json](example-api-responses/original/Communications_callRecord_v1.0.json)                           | [sanitized/Communications_callRecord_v1.0.json](example-api-responses/sanitized/Communications_callRecord_v1.0.json)                           |
| `/v1.0/communications/callRecords/getDirectRoutingCalls(fromDateTime=...,toDateTime=...)` | [original/Communications_callRecords_getDirectRoutingCalls_v1.0.json](example-api-responses/original/Communications_callRecords_getDirectRoutingCalls_v1.0.json) | [sanitized/Communications_callRecords_getDirectRoutingCalls_v1.0.json](example-api-responses/sanitized/Communications_callRecords_getDirectRoutingCalls_v1.0.json) |
| `/v1.0/communications/callRecords/getPstnCalls(fromDateTime=...,toDateTime=...)` | [original/Communications_callRecords_getPstnCalls_v1.0.json](example-api-responses/original/Communications_callRecords_getPstnCalls_v1.0.json) | [sanitized/Communications_callRecords_getPstnCalls_v1.0.json](example-api-responses/sanitized/Communications_callRecords_getPstnCalls_v1.0.json) |
| `/v1.0/users/{userId}/onlineMeetings`                                          | [original/Users_onlineMeetings_v1.0.json](example-api-responses/original/Users_onlineMeetings_v1.0.json)                                     | [sanitized/Users_onlineMeetings_v1.0.json](example-api-responses/sanitized/Users_onlineMeetings_v1.0.json)                                     |
| `/v1.0/users/{userId}/onlineMeetings/{meetingId}/attendanceReports`            | [original/Users_onlineMeetings_attendanceReports_v1.0.json](example-api-responses/original/Users_onlineMeetings_attendanceReports_v1.0.json) | [sanitized/Users_onlineMeetings_attendanceReports_v1.0.json](example-api-responses/sanitized/Users_onlineMeetings_attendanceReports_v1.0.json) |
| `/v1.0/users/{userId}/onlineMeetings/{meetingId}/attendanceReports/{reportId}` | [original/Users_onlineMeetings_attendanceReport_v1.0.json](example-api-responses/original/Users_onlineMeetings_attendanceReport_v1.0.json)   | [sanitized/Users_onlineMeetings_attendanceReport_v1.0.json](example-api-responses/sanitized/Users_onlineMeetings_attendanceReport_v1.0.json)   |
|

See more examples in the `docs/sources/microsoft-365/msft-teams/example-api-responses` folder of the [Psoxy repository](https://github.com/Worklytics/psoxy).

**Note : Pseudonymizing App Ids**

In case of `PSEUDONYMIZE_APP_IDS` is set to `true` (default value), the `userId` and `chatId` fields will be tokenized. In such case and if you want to populate example variables like `example_msft_user_guid` or `example_msft_chat_guid` in the example responses, you will need first to get a list of user and use the `id` in the variable. Using a plain user id without tokenization might not work on endpoints that require a tokenized user id.

### Finding real callRecord / onlineMeeting / channel / chat values

By default, `msft_teams_example_call_record_guid`, `msft_teams_example_online_meeting_join_url`, `msft_teams_example_team_guid`, `msft_teams_example_channel_guid`, and `msft_teams_example_chat_guid` (part of `msft_365_connector_settings`; see the [Microsoft 365 README](../README.md#example-api-calls)) are left as placeholders, since Terraform cannot enumerate real ids from your tenant — and for the channel/chat ids, not just any team/channel/chat will do, since the example calls to `.../messages` need one that actually has messages. Rather than hunting for these by hand, run `tools/psoxy-test/find-msft-teams-example-values.js` against your deployed connector; it will fetch a real call record, find a real online meeting (by resolving a meeting chat's `joinWebUrl`), and find a real team/channel and chat that each have messages. See [Psoxy test tool](../../../guides/psoxy-test-tool.md#microsoft-teams-finding-a-call-record-online-meeting-channel-or-chat).

`/v1.0/communications/calls/{callId}` (`msft_teams_example_call_guid`) can't be discovered this way: Microsoft Graph has no endpoint to list existing calls, so there's no id to find outside of your own calling-bot integration.

## Example Rules

- [Example Rules](msft-teams.yaml)
- [Example Rules: no User IDs](msft-teams_no-userIds.yaml)