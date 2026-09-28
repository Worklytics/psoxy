# Psoxy testing tool

Node.js testing tool for Worklytics Psoxy.

We provide a collection of Node.js scripts to help you test your Worklytics Psoxy deploy. The requirements to be able to run the scripts are [Node.js] (version >=16) and [npm] (version >=8). First of all, install the npm dependencies: `npm i`.

The primary tool is a command line interface (CLI) script that allows you to execute "Psoxy Test Calls" to your Worklytics Psoxy instance. Check all the available options by running `node cli-call.js -h` (*).

We also provide a script to test "Psoxy bulk instances": they consist of an input bucket, an output one, and the Psoxy instance itself. The script allows you to upload a comma-separated values file (CSV) to the input bucket, it will check that the Psoxy has processed the file and have written it to the output bucket removing all Personal Identifiable Information (PII) from the file (as per Psoxy rules). Check available options by running `node cli-file-upload.js -h` (*).

A third script lets you check your Psoxy instance logs: `node cli-logs.js -h` (*).

(*) Options may vary depending on whether you've deployed the Worklytics Psoxy to Amazon Web Services ([AWS]) or Google Cloud Platform ([GCP]).

## Psoxy Test Call: AWS
Assuming that you've successfully deployed the Psoxy to AWS, and you've configured [Google Calendar] as data source, let's see an example:
```shell
node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws/calendar/v3/calendars/primary -r arn:aws:iam::310635719553:role/PsoxyApiCaller -i user@acme.com
```
The `-r` option is mandatory for AWS deploys, and identifies the Amazon Resource Name (ARN) of the "role" that will be assumed (*) to be able to execute the call. The `-u` option is the URL you want to test. In this case, the URL's path matches a Google Calendar API endpoint (access the primary calendar of the currently logged-in user). The `-i` option identifies the user "to impersonate"; this option is only relevant for Google Workspace data sources.

Another example for [Zoom]:
```shell
node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws/v2/users -r arn:aws:iam::310635719553:role/PsoxyApiCaller
```
As you can see, the differences are:
1. As this is not a Google Workspace data source, you don't need the `-i` option.
2. The URL's path matches a [Zoom API endpoint] in this case

(*) Requests to AWS API need to be [signed], so you must ensure that the machine running these scripts have the appropriate AWS credentials for the role you've selected.

## Psoxy Test Call: GCP
For GCP, every call needs an "identity token" (`-t, --token` option in the examples below) for the account that has access to the Cloud Platform (*). If you omit the token, the script will try to get it automatically, so you must [authorize gcloud first].

Google Calendar example:
```shell
node cli-call.js -u https://us-central1-acme.cloudfunctions.net/calendar/v3/calendars/primary -t <IDENTITY_TOKEN> -i user@acme.com
```
Zoom example:
```shell
node cli-call.js -u https://us-central1-acme.cloudfunctions.net/v2/users -t <IDENTITY_TOKEN>
```
Outlook Calendar example (token option omitted):
```shell
node cli-call.js -u https://us-central1-acme.cloudfunctions.net/outlook-cal/v1.0/users
```

#### External ALB URLs (beta)

When API connectors are fronted by an external Application Load Balancer (ALB), test URLs look like `https://<domain-or-ip>/<function-name>/...` rather than `*.run.app`. Use:

- `-f gcp` so the tool treats the host as a GCP-hosted deployment (required when the hostname is not `*.run.app` / `*.cloudfunctions.net`)
- `--allow-insecure-tls` for PoC self-signed certs on a reserved global IP, **or** `--cacert <path>` to trust the PEM from Terraform output `external_api_alb.self_signed_ca_cert`

```shell
node cli-call.js -u https://203.0.113.10/myenv-outlook-cal/ -f gcp --allow-insecure-tls --health-check
```

Generated test scripts from Terraform add these flags when an external LB base URL is set (`external_api_alb` on `gcp-host`, or BYO `api_connector_external_lb_host`). When `allowed_data_access_ip_blocks` is set, your client IP must be allowlisted (Cloud Armor + app layer) to reach the ALB. See [GCP External Application Load Balancer (ALB) + Cloud Armor](../development/gcp-external-alb.md).

**Common errors when testing through an external Application Load Balancer (ALB):**

| Symptom | Likely cause | What to check |
|---|---|---|
| `ECONNRESET` / "socket disconnected before secure TLS connection was established" | ALB not ready yet, or transient propagation | Wait after `terraform apply`; confirm TLS with `curl -vk https://<alb-ip>/...`. Not usually fixed by changing the allowlist. Use `--allow-insecure-tls` for PoC self-signed certs. |
| `403 Forbidden` with a minimal HTML page (`<title>403</title>`) | Cloud Armor blocked your source IP | Add the IP you dial **from** to `allowed_data_access_ip_blocks` (include **both** IPv4 and IPv6 if unsure). Verify with `curl -4/-6 ifconfig.me` and [troubleshooting in the ALB doc](../development/gcp-external-alb.md#403-forbidden-minimal-html-page-title403title403-forbidden). |

(*) You can obtain it by running `gcloud auth print-identity-token` (using [Google Cloud SDK])

### End-to-End Verification (Webhook Collection)

For Webhook Collection testing, you can use the tool to verify that the data was successfully collected and written to the expected bucket.

```shell
node cli-call.js -u https://us-central1-acme.cloudfunctions.net/webhook-collector --method POST --body '{...}' --verify-collection my-output-bucket
```

This will:
1. Make the POST request to the webhook collector.
2. In GCP case, trigger the associated Cloud Scheduler job processing the batch (GCP).
3. Poll the specified bucket until the output file appears (up to 60s).
4. Verify that the content of the output file matches the uploaded data.

**Options:**
*   `--verify-collection <bucketName>`: Enables verification mode and specifies the target bucket.
*   `--scheduler-job <jobName>`: (only for GCP case) Specify the Cloud Scheduler job that batch-processes pending webhooks from the pubsub topic.

### Psoxy Test Call: Health Check option
Use the `--health-check` option to check if your deploy is correctly configured:

```shell
# Example for AWS deploys
node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws -r <ROLE> --health-check
```

```shell
# Example for GCP deploys
node cli-call.js -u https://us-central1-acme.cloudfunctions.net -t <IDENTITY_TOKEN> --health-check
```

Example response for Zoom:
```json
 {
  "configuredSource": "zoom",
  "missingConfigProperties": [],
  "nonDefaultSalt": true
}
```

### Psoxy Test Call: testing all endpoints for a given data source

The `-d, --data-source` option of our CLI script allows you to test all the endpoints for a given data source (available data sources are listed in the script's help: `-h` option).
The only difference with the previous examples is that the `-u, --url` option has to be the URL of the deploy **without** the corresponding API path of the data source:

```shell
# Zoom example for AWS deploys, instead of running a single call:
# node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws/v2/users -r <role>
# use this command to run multiple calls:
node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws -r <ROLE> -d zoom
```
```shell
# Zoom example for GCP deploys, instead of running a single call:
# node cli-call.js -u https://us-central1-acme.cloudfunctions.net/v2/users -t <ROLE>
# use this command to run multiple calls:
node cli-call.js -u https://us-central1-acme.cloudfunctions.net -t <IDENTITY_TOKEN> -d zoom
# or simply:
node cli-call.js -u https://us-central1-acme.cloudfunctions.net -d zoom
```

Notice how the URL changes, and any other option the Psoxy may need doesn't.

### Zoom: finding a meeting that has a summary

`GET /v2/meetings/{MEETING_ID}/meeting_summary` requires a past meeting instance UUID where Zoom AI Companion actually produced a meeting summary. Numeric IDs from `GET /v2/users/{USER_ID}/meetings` are scheduled meetings and usually return `Invalid meeting id`.

`find-zoom-meeting-summary.js` (in `tools/psoxy-test/`, next to `cli-call.js`) walks users → past meetings → instances until `has_meeting_summary` is true, then fetches the summary. Pass the Zoom function **base URL** (no API path) and the same flags you use with `cli-call.js` / `test-zoom.sh`:

```shell
# GCP (including external ALB)
node find-zoom-meeting-summary.js -u https://us-central1-acme.cloudfunctions.net/psoxy-zoom -f gcp --allow-insecure-tls
# AWS
node find-zoom-meeting-summary.js -u https://acme.lambda-url.us-east-1.on.aws -r <ROLE>
```

Useful flags: `--lookback-days 180` (report meetings are searched in 30-day windows), `--max-users 5`. See [Zoom example API calls](../../docs/sources/zoom/README.md#example-api-calls).

### Microsoft Teams: finding a call record, online meeting, channel, or chat

Several `msft-teams` example calls need an id Terraform can't enumerate on its own:
- `GET /v1.0/communications/callRecords/{callRecordId}` needs a real call record id.
- `GET /v1.0/users/{userId}/onlineMeetings/{meetingId}` (and its `attendanceReports`) needs a real online meeting, which Graph can only look up by `joinWebUrl`, not by listing.
- `GET /v1.0/teams/{teamId}/channels/{channelId}/messages` (and `/delta`) need a team/channel that actually has messages, not just any team/channel.
- `GET /v1.0/chats/{chatId}/messages` needs a chat that actually has messages.

(`GET /v1.0/communications/calls/{callId}` is not covered by any script: Graph has no endpoint to list existing calls — a `call` resource only exists for the lifetime of a session created by a calling bot, so its id is only known to whatever created it.)

`find-msft-teams-example-values.js` (in `tools/psoxy-test/`, next to `cli-call.js`) covers all four:
- a real call record (`GET /communications/callRecords` → first result)
- an online meeting, by listing each user's chats where `onlineMeetingInfo/joinWebUrl` is set and resolving that URL against `onlineMeetings`
- a team/channel with messages, by walking teams → `allChannels` → channel messages until one is non-empty
- a chat with messages, by walking users → chats → chat messages until one is non-empty

Pass the msft-teams function **base URL** (no API path) and the same flags you use with `cli-call.js` / `test-msft-teams.sh`:

```shell
# GCP (including external ALB)
node find-msft-teams-example-values.js -u https://us-central1-acme.cloudfunctions.net/psoxy-msft-teams -f gcp --allow-insecure-tls
# AWS
node find-msft-teams-example-values.js -u https://acme.lambda-url.us-east-1.on.aws -r <ROLE>
```

By default it looks for all four. Use `--target call-record` / `online-meeting` / `team-channel` / `chat` to look for just one, or `--skip-call-record` / `--skip-online-meeting` / `--skip-team-channel` / `--skip-chat` (and `--skip-calls-note`) to exclude any combination — e.g. `--skip-call-record --skip-team-channel --skip-chat` to look only for an online meeting. Useful flags: `--max-users 20` (online-meeting/chat search), `--max-teams 20` (team-channel search), `--page-size 100`.

### Microsoft OneDrive: finding a drive and drive item

`GET /v1.0/drives/{driveId}/root/delta`, `.../activities`, and `.../items/{itemId}/activities` need a real `Drive.id` and `driveItem.id`, which Terraform cannot enumerate on its own (see `msft_onedrive_example_drive_id`/`msft_onedrive_example_item_id` in `msft_365_connector_settings`).

`find-msft-onedrive-example-values.js` checks each user's (then each group's) `/drives` for one that exists, then looks at that drive's `root/delta` feed for a real item (preferring an actual file over a folder). Pass the msft-onedrive function **base URL** (no API path) and the same flags you use with `cli-call.js` / `test-msft-onedrive.sh`:

```shell
# GCP (including external ALB)
node find-msft-onedrive-example-values.js -u https://us-central1-acme.cloudfunctions.net/psoxy-msft-onedrive -f gcp --allow-insecure-tls
# AWS
node find-msft-onedrive-example-values.js -u https://acme.lambda-url.us-east-1.on.aws -r <ROLE>
```

Use `--source users` or `--source groups` to check just one (equivalently, `--skip-groups` or `--skip-users`); useful flags: `--max-owners 20`, `--page-size 100`.

## Psoxy Logs: AWS
Assuming that you've successfully deployed the Psoxy to AWS, you can inspect the logs by running the following command:

```shell
node cli-logs.js -r <role> --region <region> -l <logGroupName>
```

## Psoxy Logs: GCP
Use the following command to review the runtime logs of your Psoxy deploy to GCP:

```shell
node cli-logs.js -p <projectId> -f <functionName>
```

The `<projectId>` option is the Google Cloud project identifier that hosts your
Psoxy deploy, and the `<functionName>` option is the identifier of the
Cloud Function that represents the Psoxy instance itself.


## Psoxy Bulk Instances: AWS
Assuming that you've successfully deployed the Psoxy "bulk instance" to AWS, you need to provide
the script with a CSV example file containing some PII records, the name of the input bucket and
the output one (these are expected to be [S3] buckets in the same AWS region). The script also
needs the AWS region (default is `us-east-1`), and the ARN of the role that will be assumed to
perform the upload and download operations.

Example:
```shell
node cli-file-upload.js -d AWS -i input-bucket-name -o output-bucket-name -f /path/to/file.csv -r <ROLE> --region <REGION>
```

## Psoxy Bulk Instances: GCP
Use the following command to test a Psoxy "bulk" instance deployed to GCP:

```shell
node cli-file-upload.js -d GCP -i input-bucket-name -o output-bucket-name -f /path/to/file.csv
```

In this case, `-i` and `-o` options represent [Google Cloud Storage] buckets.

The testing script will rename the files you upload by appending a timestamp value as suffix:
`my-test-file.csv` will appear as `my-test-file-{timestamp}.csv` in both the input and output
buckets. This is done to avoid conflicts with files that may already exist in the buckets.

By default, the sanitized file will be deleted from the output bucket after the comparison
test (original file vs. sanitized one). Run `node cli-file-upload.js -h` to see all the
available options (keep sanitized file in the output bucket, save it to disk, etc).

[AWS]: https://aws.amazon.com
[GCP]: https://cloud.google.com/
[Node.js]: https://nodejs.org/en/
[npm]: https://www.npmjs.com
[signed]: https://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html
[Google Calendar]: https://developers.google.com/calendar/api
[Zoom]: https://zoom.us
[Zoom API endpoint]: https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/users
[Google Cloud SDK]: https://cloud.google.com/sdk/gcloud/reference/auth/print-identity-token
[authorize gcloud first]: https://cloud.google.com/sdk/gcloud/reference/auth/login
[S3]: https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html
[Google Cloud Storage]: https://cloud.google.com/storage
