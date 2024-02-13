# Zoom

## Prerequisites

As of July 2023, pulling historical data (last 6 months) and all scheduled and instant meetings
requires a Zoom paid account on Pro or higher plan (Business, Business Plus). On other plans Zoom
data may be incomplete.

Accounts on unpaid plans do not have access to some methods Worklytics use like:

- [Zoom Reports API](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Reports)
  -required for historical data
- certain
  [Zoom Meeting API](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Meetings)
  methods such as retrieving
  [past meeting participants](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/pastMeetingParticipants)

## Examples

- [Example Rules](zoom.yaml)
- Example Data : [original](example-api-responses/original) |
  [sanitized](example-api-responses/sanitized)

## Steps to Connect

The Zoom connector through Psoxy requires a Custom Managed App on the Zoom Marketplace. This app may
be left in development mode; it does not need to be published.

1. Go to https://marketplace.zoom.us/develop/create and create an app of type "Server to Server
   OAuth"
2. After creation, it will show the App Credentials.

   Copy the following values:

   - `Account ID`
   - `Client ID`
   - `Client Secret`

   ![Server to Server OAuth App](server-to-server-oauth-app.png)

   Share them with the AWS/GCP administrator, who should fill them in your host platform's secret
   manager (AWS Systems Manager Parameter Store / GCP Secret Manager) for use by the proxy when
   authenticating with the Zoom API:

   - `Account ID` --> `PSOXY_ZOOM_ACCOUNT_ID`
   - `Client ID` --> `PSOXY_ZOOM_CLIENT_ID`
   - `Client Secret` --> `PSOXY_ZOOM_CLIENT_SECRET`

   NOTE: Anytime the _Client Secret_ is regenerated it needs to be updated in the Proxy too. NOTE:
   _Client Secret_ should be handled according to your organization's security policies for API
   keys/secrets as, in combination with the above, allows access to your organization's data.

3. Fill the information section

4. Fill the scopes section, enabling the following:

   - Users / View all user information / `user:read:admin`
     - List information about the Zoom user accounts, for enumeration / linking across sources
   - Meetings / View all user meetings / `meeting:read:admin`
     - Listing all user meetings (work events / items)
   - Report / View report data / `report:read:admin`
     - List historical (previous 6 months) user meetings (work events / items)

5. Activate the app
