# Gemini in Workspace Apps

**Connector ID:** `gemini-in-workspace-apps`

**Availability:** Beta

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

This connector pulls Gemini-events from the Google Workspace audit log.

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

- [Example Rules](gemini-in-workspace-apps.yaml)
- Example Data:
    - [original/admin_reports_v1_activity_users_{userKey}_applications_gemini_in_workspace_apps.json](example-api-responses/original/admin_reports_v1_activity_users_{userKey}_applications_gemini_in_workspace_apps.json) |
      [sanitized/admin_reports_v1_activity_users_{userKey}_applications_gemini_in_workspace_apps.json](example-api-responses/sanitized/admin_reports_v1_activity_users_{userKey}_applications_gemini_in_workspace_apps.json)


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
