# Google Chat&trade;

**Connector ID:** `google-chat`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

## Required OAuth Scopes

- `admin.reports.audit.readonly`

For Domain-wide Delegation in the Google Workspace Admin console, paste the following comma-separated list into the **Scopes** field:

```
https://www.googleapis.com/auth/admin.reports.audit.readonly
```

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `admin.googleapis.com` (Admin SDK API)

## Examples

- [Example Rules](google-chat.yaml)
- Example Data:
  - [original/chat-activities.json](example-api-responses/original/chat-activities.json) |
    [sanitized/chat-activities.json](example-api-responses/sanitized/chat-activities.json)


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
