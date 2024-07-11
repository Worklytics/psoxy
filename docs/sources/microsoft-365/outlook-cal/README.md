# Outlook Calendar

Connect Outlook Calendar data to Worklytics, enabling meeting analysis and general collaboration
insights based on collaboration via Outlook Calendar. Includes user enumeration to support fetching
calendars from each account; and group enumeration to expand attendance/invitations to meetings
via mailing list (groups).

## Required Scopes
- [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall)
- [`Calendars.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#calendarsread)
- [`MailboxSettings.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailboxsettingsread)
- [`OnlineMeetings.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#onlinemeetingsreadall)

## Authentication

See the [Microsoft 365 Authentication](../README.md#authentication) section of the main README.

## Authorization

See the [Microsoft 365 Authorization](../README.md#authorization) section of the main README.


## Example Data

| API Endpoint                     | Example Response                                                                           | Sanitized Example Response                                                                     |
|----------------------------------|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `/v1.0/me/events`                | [original/Events_v1.0.json](example-api-responses/original/Events_v1.0.json)               | [sanitized/Events_v1.0.json](example-api-responses/sanitized/Events_v1.0.json)                 |
| `/v1.0/me/events/{eventId}`      | [original/Event_v1.0.json](example-api-responses/original/Event_v1.0.json)                 | [sanitized/Event_v1.0.json](example-api-responses/sanitized/Event_v1.0.json)                   |
| `/v1.0/me/calendar/calendarView` | [original/CalendarView_v1.0.json](example-api-responses/original/CalendarView_v1.0.json) | [sanitized/CalendarView_v1.0.json](example-api-responses/sanitized/CalendarView_v1.0.json) |
| `/v1.0/me/calendar/events`       | [original/CalendarEvents_v1.0.json](example-api-responses/original/CalendarEvents_v1.0.json) | [sanitized/CalendarEvents_v1.0.json](example-api-responses/sanitized/CalendarEvents_v1.0.json) |

Assuming proxy is auth'd as an application, you'll have to replace `me` with your MSFT ID or
`UserPrincipalName` (often your email address).

See more examples in the `docs/sources/microsoft-365/msft-teams/example-api-responses` folder
of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Examples

- [Example Rules](outlook-cal.yaml)
- [Example Rules: no App IDs](outlook-cal_no-app-ids.yaml)
- [Example Rules: no App IDs, no groups](outlook-cal_no-app-ids_no-groups.yaml)

