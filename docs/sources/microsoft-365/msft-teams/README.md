# Microsoft Teams

Connect Microsoft Teams data to Worklytics, enabling communication analysis and general collaboration
insights based on collaboration via Micorosft Teams. Includes user enumeration to support fetching
mailboxes from each account; and group enumeration to expand emails via mailing list (groups).

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

## Example Data

| API Endpoint                        | Example Response                                                                                               | Sanitized Example Response                                                                     |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `/v1.0/teams`                       | [original/Teams_v1.0.json](example-api-responses/original/Teams_v1.0.json)                                     | [sanitized/Teams_v1.0.json](example-api-responses/sanitized/Teams_v1.0.json)                   |
| `/v1.0/teams/{teamId}/allChannels`  | [original/Teams_allChannels_v1.0.json](example-api-responses/original/Teams_allChannels_v1.0.json)             | [sanitized/Teams_allChannels_v1.0.json](example-api-responses/sanitized/Teams_allChannels_v1.0.json) |
| `/v1.0/teams/{teamId}/channels/{channelId}/messages` | [original/Teams_channels_messages_v1.0.json](example-api-responses/original/Teams_channels_messages_v1.0.json) | [sanitized/Teams_channels_messages_v1.0.json](example-api-responses/sanitized/Teams_channels_messages_v1.0.json) |
| `/v1.0/users/{userId}/chats`        | [original/Chats_messages_v1.0.json](example-api-responses/original/Chats_messages_v1.0.json)                   | [sanitized/Chats_messages_v1.0.json](example-api-responses/sanitized/Chats_messages_v1.0.json) |
| `/v1.0/users/{userId}/onlineMeetings` | [original/Users_onlineMeetings_v1.0.json](example-api-responses/original/Users_onlineMeetings_v1.0.json)       | [sanitized/Users_onlineMeetings_v1.0.json](example-api-responses/sanitized/Users_onlineMeetings_v1.0.json) |
|
See more examples in the `docs/sources/microsoft-365/msft-teams/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Example Rules

- [Example Rules](msft-teams.yaml)
- [Example Rules: no User IDs](msft-teams_no-userIds.yaml)


