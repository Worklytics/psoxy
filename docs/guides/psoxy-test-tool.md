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

(*) You can obtain it by running `gcloud auth print-identity-token` (using [Google Cloud SDK])

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

## Psoxy Logs: AWS
Assuming that you've successfully deployed the Psoxy to AWS, you can inspect the logs by running the following command:

```shell
node cli-logs.js -r <role> -re <region> -l <logGroupName>
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
node cli-file-upload.js -d AWS -i input-bucket-name -o output-bucket-name -f /path/to/file.csv -r <ROLE> -re <REGION>
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
[Zoom API endpoint]: https://marketplace.zoom.us/docs/api-reference/zoom-api/methods/#operation/users
[Google Cloud SDK]: https://cloud.google.com/sdk/gcloud/reference/auth/print-identity-token
[authorize gcloud first]: https://cloud.google.com/sdk/gcloud/reference/auth/login
[S3]: https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html
[Google Cloud Storage]: https://cloud.google.com/storage
