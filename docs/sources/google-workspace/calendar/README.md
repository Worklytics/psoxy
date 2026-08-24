# Google Calendar&trade;

**Connector ID:** `gcal`

**Availability:** GA

Please review the [Google Workspace&trade; README](../README.md) for general information applicable to
all Google Workspace&trade; connectors.

## Required OAuth Scopes

- `calendar.readonly`

For Domain-wide Delegation in the Google Workspace Admin console, paste the following comma-separated list into the **Scopes** field:

```
https://www.googleapis.com/auth/calendar.readonly
```

## Required GCP APIs

Enable the following API in the GCP project where you provision the OAuth client:

- `calendar-json.googleapis.com` (Google Calendar API)

## Examples

- [Example Rules](calendar.yaml)
- Example Data:
  - [original/calendarList.json](example-api-responses/original/calendarList.json) |
    [sanitized/calendarList.json](example-api-responses/sanitized/calendarList.json)
  - [original/event.json](example-api-responses/original/event.json) |
    [sanitized/event.json](example-api-responses/sanitized/event.json)
  - [original/events.json](example-api-responses/original/events.json) |
    [sanitized/events.json](example-api-responses/sanitized/events.json)

Example API call path parameters: `{eventId}` is a Calendar event id from `GET /calendar/v3/calendars/primary/events` for the impersonated user. `{accountId}` in the rules is `primary` (calendar) or `me` (settings / calendarList) in the generated examples; it can also be a calendar id or user email.


---
Google Workspace&trade; and related marks are trademarks of Google LLC.
Worklytics&trade; is a trademark of Worklytics, Corp.
