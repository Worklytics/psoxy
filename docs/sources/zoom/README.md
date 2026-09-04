# Zoom

**Connector ID:** `zoom`

**Availability:** GA

## Prerequisites

As of July 2023, pulling historical data (last 6 months) and all scheduled and instant meetings
requires a Zoom paid account on Pro or higher plan (Business, Business Plus). On other plans Zoom
data may be incomplete.

Accounts on unpaid plans do not have access to some methods Worklytics use like:

- [Zoom Reports API](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Reports) -required for historical data
- certain [Zoom Meeting API](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#tag/Meetings) methods such as retrieving [past meeting participants](https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/pastMeetingParticipants)

## Examples

- [Example Rules](zoom.yaml)
- Example Data : [original/meeting-details.json](example-api-responses/original/meeting-details.json) | [sanitized/meeting-details](example-api-responses/sanitized/meeting-details.json)

See more examples in the `docs/sources/zoom/example-api-responses` folder of the [Psoxy repository](https://github.com/Worklytics/psoxy).

## Example API calls

Example commands (\*) that you can use to validate proxy behavior against the Zoom APIs. Follow the steps and change the values to match your configuration when needed.

Path parameters:

- `{USER_ID}`: a Zoom user id from `GET /v2/users` (`.users[].id`).
- `{MEETING_ID}`: a numeric meeting id from `GET /v2/users/{USER_ID}/meetings`, or a past-meeting instance UUID from `GET /v2/past_meetings/{MEETING_ID}/instances` (required for `/meeting_summary`).

For AWS, use the `-r` flag to assume an IAM role that has permission to call the proxy. Example:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If any call appears to fail, repeat it using the `-v` flag.

(\*) All commands assume that you are at the root path of the Psoxy project.

### List users

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users
```

Pull a user id (`{USER_ID}`, accessor path in response `.users[0].id`). The next calls are bound to a single user:

### List user meetings

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users/{USER_ID}/meetings
```

### List user cloud recordings

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/users/{USER_ID}/recordings
```

### List report meetings for a user

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/report/users/{USER_ID}/meetings
```

### Meetings

Pull a meeting id (`{MEETING_ID}`, accessor path in response `.meetings[0].id`):

#### Get meeting details

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/meetings/{MEETING_ID}
```

#### List past meeting details

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/{MEETING_ID}
```

#### List past meeting instances

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/{MEETING_ID}/instances
```

#### List past meeting participants

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/{MEETING_ID}/participants
```

#### List report participants for a meeting

`GET /v2/report/meetings/{MEETING_ID}` (meeting details without `/participants`) is **not** allow-listed. Use the participants report:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/report/meetings/{MEETING_ID}/participants
```

#### Get a meeting summary

`GET /v2/meetings/{MEETING_ID}/meeting_summary` only returns data for a **past meeting instance** that generated a Zoom AI Companion meeting summary. `{MEETING_ID}` must be that instance's UUID — not the numeric meeting number from `GET /v2/users/{USER_ID}/meetings`, which lists scheduled/upcoming meetings and typically yields Zoom error `300` (`Invalid meeting id`).

Use `tools/psoxy-test/find-zoom-meeting-summary.js` to walk users → past meetings → instances until `has_meeting_summary` is true, then call the summary endpoint. It lives next to `cli-call.js` and takes the Zoom function **base URL** (no API path) plus the same flags as `test-zoom.sh`:

```shell
# GCP (including external ALB)
node tools/psoxy-test/find-zoom-meeting-summary.js -u [your_psoxy_url] -f gcp --allow-insecure-tls

# AWS
node tools/psoxy-test/find-zoom-meeting-summary.js -u [your_psoxy_url] -r arn:aws:iam::PROJECT_ID:role/ROLE_NAME
```

If you don't want to use the script and already have an instance UUID (`{MEETING_ID}`), you can check with:

```shell
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/past_meetings/{MEETING_ID}
node tools/psoxy-test/cli-call.js -u [your_psoxy_url]/v2/meetings/{MEETING_ID}/meeting_summary
```

If the UUID starts with `/` or contains `//`, [double-encode](https://developers.zoom.us/docs/api/using-zoom-apis/#meeting-id-and-uuid) it in the path. Smart Recording summaries in the Zoom UI are a different feature; this endpoint only returns AI Companion **meeting** summaries.

## Steps to Connect

The Zoom connector through Psoxy requires a Custom Managed App on the Zoom Marketplace. This app may be left in development mode; it does not need to be published.

1. Go to [https://marketplace.zoom.us/develop/create](https://marketplace.zoom.us/develop/create) and create an app of type "Server to Server
OAuth" for creating a server-to-server app. (NOTE: if this option is disabled for you, the owner/super-admin for your account may need to edit permissions associated with your role via User Management > Roles > Role Settings > Advanced features in the Zoom web portal and select the View and Edit checkboxes for "Server-to-Server OAuth app.")

2. After creation, it will show the App Credentials.

   Copy the following values:

   - `Account ID`
   - `Client ID`
   - `Client Secret`

   ![Server to Server OAuth App](server-to-server-oauth-app.png)

   Share them with the AWS/GCP administrator, who should fill them in your host platform's secret manager (AWS Systems Manager Parameter Store / GCP Secret Manager) for use by the proxy when authenticating with the Zoom API:

   - `Account ID` --> `PSOXY_ZOOM_ACCOUNT_ID`
   - `Client ID` --> `PSOXY_ZOOM_CLIENT_ID`
   - `Client Secret` --> `PSOXY_ZOOM_CLIENT_SECRET`

   NOTE: Anytime the _Client Secret_ is regenerated it needs to be updated in the Proxy too. NOTE: _Client Secret_ should be handled according to your organization's security policies for API keys/secrets as, in combination with the above, allows access to your organization's data.

3. Fill the 'Information' section. Zoom requires company name, developer name, and developer email to activate the app.

4. No changes are needed in the 'Features' section. Continue.

5. Fill the scopes section clicking on `+ Add Scopes` and adding the following:

* `meeting:read:past_meeting:admin`
* `meeting:read:meeting:admin`
* `meeting:read:list_past_participants:admin`
* `meeting:read:list_past_instances:admin`
* `meeting:read:list_meetings:admin`
* `meeting:read:participant:admin`
* `meeting:read:summary:admin`
* `cloud_recording:read:list_user_recordings:admin`
* `report:read:list_meeting_participants:admin`
* `report:read:meeting:admin`
* `report:read:user:admin`
* `user:read:user:admin`
* `user:read:list_users:admin`
* `user:read:settings:admin`

    ![Scopes](scopes.png)

Once the scopes are added, click on `Done` and then `Continue`.

6. Activate the app

## Zoom AI Metric Snapshot Bulk

Psoxy can pseudonymize Zoom AI Metrics Snapshot CSV data.

The default proxy rules for `zoom-ai-metrics` will pseudonymize `User Name` and `Email`, redacting `Department`

```hcl
custom_bulk_connector_rules = {
    "zoom-ai-metrics" = {
        source_kind               = "zoom",
        worklytics_connector_id   = "bulk-import-psoxy"
        worklytics_connector_name = "Bulk Import - Psoxy"
        display_name              = "Zoom AI Metrics"
        rules = {
            columnsToPseudonymize = [
                "User Name",
                "Email"
            ],
            columnsToRedact = [
                "Department",
            ]
        }
        settings_to_provide = {
            "Parser" = "zoom-ai-metrics-bulk"
        }
    }
}
```

## Troubleshooting

### Zoom API Error : 400 invalid client

`{"reason":"Invalid client_id or client_secret","error":"invalid_client"}`

Causes:
   - extra chars in Client ID; or incorrect Client ID

Confirm that the `Client ID` and `Client Secret` are correctly set in your secret store solution (AWS Parameter Store, Secrets Manager, or GCP Secret Manager).

### Zoom API Error : 400 Bad Request

`{"reason":"Bad Request","error":"invalid_request"}`

Causes:
  - extra chars in Account ID; or incorrect Account ID