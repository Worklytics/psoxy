## Testing

By default, the Terraform examples provided by Worklytics install a NodeJS-based tool for testing your proxy deployments.

Full documentation of the test tool is available [here](psoxy-test-tool.md). And the code is located in the `tools` directory of the [Psoxy repository](https://github.com/Worklytics/psoxy).

### Testing Pre-requisites

Wherever you run this test tool from, your AWS or GCloud CLI _must_ be authenticated as an entity with permissions to invoke the Lambda functions / Cloud functions that you deployed for Psoxy.

If you're testing the bulk cases, the entity must be able to read/write to the cloud storage buckets created for each of those bulk examples.

### Testing Locally when Terraform ran remotely (eg, Terraform Cloud, GitHub Actions, etc)

If you're running the Terraform examples in a different location from where you wish to run tests, then you can install the tool alone:

1. Clone the Psoxy repo to your local machine:

    ```shell
    git clone https://github.com/Worklytics/psoxy.git
    ```

2. From within that clone, install the test tool:

    ```shell
    ./tools/install-test-tool.sh
    ```

3. Get specific test commands for your deployment

    - If you set the `todos_as_outputs` variable to `true`, your Terraform apply run should contain `todo2` output variable with testing instructions.
    - If you set `todos_as_local_files` variable to `true`, your Terraform apply run should contain local files named `TODO 2 ...` with testing instructions.

    In both cases, you will need to replace the test tool path included there with the path to your installation.

4. Example commands of the primary testing tool: "Psoxy Test Calls"

    ```shell
    # GCP deployment example:
    node cli-call.js -u https://us-central1-acme.cloudfunctions.net/calendar/v3/calendars/primary -t <IDENTITY_TOKEN> -i user@acme.com
    # AWS deployment example:
    node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws/v2/users -r arn:aws:iam::310635719553:role/PsoxyApiCaller
    ```

### Zoom meeting summaries

`GET /v2/meetings/{MEETING_ID}/meeting_summary` only works for a **past meeting instance** that generated a Zoom AI Companion meeting summary. Generated `test-zoom.sh` examples use a `{MEETING_ID}` placeholder; passing a numeric id from `GET /v2/users/{USER_ID}/meetings` (scheduled meetings) typically returns Zoom error `300` / `Invalid meeting id`.

`tools/psoxy-test/find-zoom-meeting-summary.js` uses the same HTTP helpers as `cli-call.js` to walk users → past meetings → instances until `has_meeting_summary` is true, then calls the summary endpoint. Pass the Zoom function **base URL** (no API path) plus the same flags as `test-zoom.sh`:

```shell
# GCP (including external ALB)
node tools/psoxy-test/find-zoom-meeting-summary.js -u https://us-central1-acme.cloudfunctions.net/psoxy-zoom -f gcp
# AWS
node tools/psoxy-test/find-zoom-meeting-summary.js -u https://acme.lambda-url.us-east-1.on.aws -r arn:aws:iam::310635719553:role/PsoxyApiCaller
```

See [Zoom example API calls](../sources/zoom/example-api-calls.md) and the [Psoxy test tool](psoxy-test-tool.md) for details.

### Testing Deployments made without Terraform

If you used and approach other than Terraform, or did not directly use our Terraform examples, you may not have the testing examples or the test tool installed on your machine.

In such a case, you can install the test tool manually by following steps 1+2 above, and then can review the documentation on how to use it from your machine.
