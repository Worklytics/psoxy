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

`OnlineMeetings.Read.All` and `OnlineMeetingArtifact.Read.All` also require a tenant-wide application access policy. A Teams Administrator must create and grant that policy with the Microsoft Teams PowerShell module. Entra admin consent does not create it. See [Configure Access to Online Meetings](#configure-access-to-online-meetings).

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

### Configure Access to Online Meetings

Online meeting and meeting-artifact calls require an [application access policy](https://learn.microsoft.com/en-us/graph/cloud-communication-online-meeting-application-access-policy) in addition to the `OnlineMeetings.Read.All` and `OnlineMeetingArtifact.Read.All` application permissions. A Teams Administrator creates that policy with the [Microsoft Teams PowerShell module](https://learn.microsoft.com/en-us/microsoftteams/teams-powershell-install) and grants it to the whole tenant, naming this connector's application (client) ID. Entra admin consent does not create the policy.

Until the policy is granted, those endpoints return `403 Forbidden` with message `No application access policy found for this app`. Policy changes can take up to 30 minutes to take effect.

1. Assign the **Teams Administrator** role to the account that will run the commands, in the [Microsoft 365 admin center](https://learn.microsoft.com/en-us/microsoft-365/admin/add-users/assign-admin-roles?view=o365-worldwide#assign-a-user-to-an-admin-role-from-active-users) or in the Azure portal (Microsoft Entra ID → Users → Assign roles). The Teams Administrator role is sometimes missing from the Entra admin center even for an admin account; assign it in the Azure portal in that case.

2. Install the [Microsoft Teams PowerShell module](https://learn.microsoft.com/en-us/microsoftteams/teams-powershell-install).

3. Connect and sign in as that Teams Administrator:

```powershell
Connect-MicrosoftTeams
```

4. Create a policy that includes the connector application's client ID. Terraform setup output fills this in; otherwise substitute the application (client) ID for `<application_id>`:

```powershell
New-CsApplicationAccessPolicy -Identity Teams-Policy-For-Worklytics -AppIds "<application_id>" -Description "Policy for MSFT Teams used for Worklytics Psoxy connector"
```

5. Grant that policy to the whole tenant, so the connector can read meetings organized by any user:

```powershell
Grant-CsApplicationAccessPolicy -PolicyName Teams-Policy-For-Worklytics -Global
```

**Issues**:

- `Access denied` means the signed-in account does not yet have the Teams Administrator role. Assign the role, then close and reopen PowerShell before connecting again.
- These commands were verified on PowerShell 7.4.0 on Windows (Microsoft Store) with MicrosoftTeams module 5.8.0.

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

Terraform-generated example calls use uppercase placeholders: `{EXAMPLE_MSFT_USER_GUID}` / `{EXAMPLE_MSFT_TEAMS_TEAM_GUID}` / `{EXAMPLE_MSFT_TEAMS_CHANNEL_GUID}` / `{EXAMPLE_MSFT_TEAMS_CHAT_GUID}` from `GET /v1.0/users`, `GET /v1.0/teams`, `GET .../allChannels`, and `GET .../chats`. `{MEETING_ID}` is an onlineMeeting id from `GET /v1.0/users/{EXAMPLE_MSFT_USER_GUID}/onlineMeetings`. `{REPORT_ID}` is an attendance report id from `GET .../attendanceReports`. `{EXAMPLE_MSFT_TEAMS_CALL_GUID}` / `{EXAMPLE_MSFT_TEAMS_CALL_RECORD_GUID}` are GUIDs from `GET /v1.0/communications/calls/...` and `GET /v1.0/communications/callRecords` (`{EXAMPLE_MSFT_TEAMS_CALL_RECORD_GUID}` must be UUID-shaped). `{EXAMPLE_MSFT_TEAMS_ONLINE_MEETING_URL}` is the online-meeting join URL used as the `JoinWebUrl` filter.

See more examples in the `docs/sources/microsoft-365/msft-teams/example-api-responses` folder of the [Psoxy repository](https://github.com/Worklytics/psoxy).

**Note : Pseudonymizing App Ids**

In case of `PSEUDONYMIZE_APP_IDS` is set to `true` (default value), the `userId` and `chatId` fields will be tokenized. In such case and if you want to populate example variables like `example_msft_user_guid` or `example_msft_chat_guid` in the example responses, you will need first to get a list of user and use the `id` in the variable. Using a plain user id without tokenization might not work on endpoints that require a tokenized user id.

### Finding real callRecord / onlineMeeting / channel / chat values

By default, `msft_teams_example_call_record_guid`, `msft_teams_example_online_meeting_join_url`, `msft_teams_example_team_guid`, `msft_teams_example_channel_guid`, and `msft_teams_example_chat_guid` (part of `msft_365_connector_settings`; see the [Microsoft 365 README](../README.md#example-api-calls)) are left as placeholders, since Terraform cannot enumerate real ids from your tenant — and for the channel/chat ids, not just any team/channel/chat will do, since the example calls to `.../messages` need one that actually has messages. Rather than hunting for these by hand, run `tools/psoxy-test/find-msft-teams-example-values.js` against your deployed connector; it will fetch a real call record, find a real online meeting (by resolving a meeting chat's `joinWebUrl`), and find a real team/channel and chat that each have messages. See [Psoxy test tool](../../../guides/psoxy-test-tool.md#microsoft-teams-finding-a-call-record-online-meeting-channel-or-chat).

`/v1.0/communications/calls/{callId}` (`msft_teams_example_call_guid`) can't be discovered this way: Microsoft Graph has no endpoint to list existing calls, so there's no id to find outside of your own calling-bot integration.

## Example Rules

- [Example Rules](msft-teams.yaml)
- [Example Rules: no User IDs](msft-teams_no-userIds.yaml)