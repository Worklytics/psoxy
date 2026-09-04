# Google Drive Audit Log

**Connector ID:** `gdrive-log`

**Availability:** Beta

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace connectors.

This connector pulls Drive events from the Google Workspace audit log (Reports API), rather than the Drive files API used by the [`gdrive`](../gdrive/README.md) connector.

## Caveats

Relative to [`gdrive`](../gdrive/README.md), this source includes additional activity types that the Drive files API does not expose as an event stream — for example views, comments, downloads, and previews, in addition to creates, edits, and sharing changes.

The tradeoff is lookback. Google retains Drive audit-log events for about **6 months** (180 days). Older activity is not available from the Reports API, even if the files themselves still exist. Historical collaboration that predates that window is better covered by the files API connector.

Activity records can include the actor's client IP, which is often a residential address. Those values are hashed with the `hashIp` transform (emitted as `t~...` tokens), not passed through in the clear.

See Google's [Drive activity report](https://developers.google.com/workspace/admin/reports/v1/guides/manage-audit-drive) and [data retention](https://support.google.com/a/answer/7061566) documentation.

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

- [Example Rules](gdrive-log.yaml)
- Example Data:
  - [original/drive-activities.json](example-api-responses/original/drive-activities.json) |
    [sanitized/drive-activities.json](example-api-responses/sanitized/drive-activities.json)


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
