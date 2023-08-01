# Zoom

## Prerequisites
To be able to pull historical data (last 6 months) and all scheduled and instant meetings a Zoom paid account on Pro or higher plan (Business, Business Plus) is required. Otherwise, Zoom data will be incomplete.

Non paid accounts do not have access to some methods Worklytics use like:
- [Zoom Reports API](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Reports) needed for historical data
- certain [Zoom Meeting API](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Meetings) methods like retrieving [past meeting participants](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/pastMeetingParticipants)


## Examples

  * [Example Rules](example-rules/zoom/zoom.yaml)
  * Example Data : [original](api-response-examples/zoom) | [sanitized](api-response-examples/zoom/sanitized)

## Steps to Connect
Zoom connector through Psoxy requires a custom managed app on the Zoom Marketplace (in development
mode, no need to publish).

1. Go to https://marketplace.zoom.us/develop/create and create an app of type "Server to Server OAuth"
2. After creation, it will show the App Credentials. Share them with the AWS/GCP administrator, the
   following values must be filled in the AWS Systems Manager Parameter Store / GCP Secret Manager
   for use by the proxy when authenticating with the Zoom API::

    - `PSOXY_ZOOM_CLIENT_ID`
    - `PSOXY_ZOOM_ACCOUNT_ID`
    - `PSOXY_ZOOM_CLIENT_SECRET`

    - NOTE: Anytime the *client secret* is regenerated it needs to be updated in the Proxy too.
    - NOTE: *client secret* should be handled according to your organization's security policies for
      API keys/secrets as, in combination with the above, allows access to your organization's data.

3. Fill the information section

4. Fill the scopes section, enabling the following:

    - Users / View all user information / `user:read:admin`
        - List information about the zoom user acounts, for enumeration / linking
    - Meetings / View all user meetings / `meeting:read:admin`
        - Listing all user meetings (work events / items)
    - Report / View report data / `report:read:admin`
        - List historical (previous 6 months) user meetings (work events / items)

5. Activate the app
