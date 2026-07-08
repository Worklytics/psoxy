# Gmail&trade;

**Connector ID:** `gmail`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

## Required OAuth Scopes
- `gmail.metadata`

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `gmail.googleapis.com` (Gmail API)

## Domain-wide Delegation Scopes

Paste the following comma-separated list into the **Scopes** field when granting Domain-wide Delegation in the Google Workspace Admin console:

```
https://www.googleapis.com/auth/gmail.metadata
```

## Examples

- [Example Rules](gmail.yaml)
- Example Data:
  - [original/message.json](example-api-responses/original/message.json) |
    [sanitized/message.json](example-api-responses/sanitized/message.json)



---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
