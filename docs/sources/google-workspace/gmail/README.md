# Gmail&trade;

**Connector ID:** `gmail`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

## Required OAuth Scopes

- `gmail.metadata`

For Domain-wide Delegation in the Google Workspace Admin console, paste the following comma-separated list into the **Scopes** field:

```
https://www.googleapis.com/auth/gmail.metadata
```

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `gmail.googleapis.com` (Gmail API)

## Examples

- [Example Rules](gmail.yaml)
- Example Data:
  - [original/message.json](example-api-responses/original/message.json) |
    [sanitized/message.json](example-api-responses/sanitized/message.json)

Example API call path parameters: `{mailboxId}` in the rules is `me` in the generated examples (the impersonated user). `{messageId}` is a Gmail message id from `GET /gmail/v1/users/me/messages`.



---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
