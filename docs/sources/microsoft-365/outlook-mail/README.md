# Outlook Mail

Connect Outlook Mail data to Worklytics, enabling communication analysis and general collaboration
insights based on collaboration via Outlook Mail. Includes user enumeration to support fetching
mailboxes from each account; and group enumeration to expand emails via mailing list (groups).

## Required Scopes
- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`MailboxSettings.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailboxsettingsread)
- [`Mail.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailreadbasicall)

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.

## Example Data

| API Endpoint                     | Example Response                                                                           | Sanitized Example Response                                                                     |
|----------------------------------|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `/v1.0/me/mailFolders/SentItems/messages` | [original/Messages_SentItems_v1.0.json](example-api-responses/original/Messages_SentItems_v1.0.json) | [sanitized/Messages_SentItems_v1.0.json](example-api-responses/sanitized/Messages_SentItems_v1.0.json) |
| `/v1.0/me/messages/{messageId}`      | [original/Message_v1.0.json](example-api-responses/original/Message_v1.0.json)                 | [sanitized/Message_v1.0.json](example-api-responses/sanitized/Message_v1.0.json)                   |
| `/v1.0/me/mailboxSettings` | [original/MailboxSettings_v1.0.json](example-api-responses/original/MailboxSettings_v1.0.json) | [sanitized/MailboxSettings_v1.0.json](example-api-responses/sanitized/MailboxSettings_v1.0.json) |

Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or
`UserPrincipalName` (often your email address).

See more examples in the `docs/sources/microsoft-365/msft-teams/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Examples

- [Example Rules](outlook-mail.yaml)
- [Example Rules: no App IDs](outlook-mail_no-app-ids.yaml)
- [Example Rules: no App IDs, no groups](outlook-mail_no-app-ids_no-groups.yaml)
