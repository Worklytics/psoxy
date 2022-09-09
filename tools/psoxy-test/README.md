# Psoxy testing tool

Node.js testing tool for Worklytics Psoxy.

This folder contains Node.js scripts to help you test your Worklytics Psoxy deploy. The requirements to be able to run the scripts are [Node.js] (version >=16) and [npm] (version >=8). First of all, install the npm dependencies: `npm i`.

The primary tool is a command line interface (CLI) script that allows you to execute single "test calls" to your Worklytics Psoxy instance.  Check all the available options by running `node cli.js -h`. Those options may vary depending on whether you've deployed the Worklytics Psoxy to Amazon Web Services ([AWS]) or Google Cloud Platform ([GCP]).

## AWS
Assuming that you've successfully deployed the Psoxy to AWS, and you've configured [Google Calendar] as data source, let's see an example:
```shell
node cli.js -u https://acme.lambda-url.us-east-1.on.aws/calendar/v3/calendars/primary -r arn:aws:iam::310635719553:role/PsoxyApiCaller -i user@acme.com
```
The `-r` option is mandatory for AWS deploys, and identifies the Amazon Resource Name (ARN) of the "role" that will be assumed (*) to be able to execute the call. The `-u` option is the URL you want to test. In this case, the URL's path matches a Google Calendar API endpoint (access the primary calendar of the currently logged-in user). The `-i` option identifies the user "to impersonate"; this option is only relevant for Google Workspace data sources.

Another example for [Zoom]:
```shell
node cli.js -u https://acme.lambda-url.us-east-1.on.aws/v2/users -r arn:aws:iam::310635719553:role/PsoxyApiCaller
```
As you can see, the differences are:
1. As this is not a Google Workspace data source, you don't need the `-i` option.
2. The URL's path matches a [Zoom API endpoint] in this case

(*) Requests to AWS API need to be [signed], so you must ensure that the machine running these scripts have the appropriate AWS credentials for the role you've selected.

## GCP
For GCP, every call must include the `-t` option, which is the identity token used for authentication (*). 
Google Calendar example:
```shell
node cli.js -u https://us-central1-acme.cloudfunctions.net/psoxy-gcal/calendar/v3/calendars/primary -t <IDENTITY_TOKEN> -i user@acme.com
```
Zoom example:
```shell
node cli.js -u https://us-central1-acme.cloudfunctions.net/v2/users -t <IDENTITY_TOKEN>
```

(*) You can obtain it by running `gcloud auth print-identity-token` (using Google Cloud SDK)

## Testing all endpoints for a given data source
We also provide some npm scripts that allow you to test all the endpoints for a given data source. These scripts rely on the same Node.js module that runs the CLI script described above, but instead of providing the options via CLI, you have to create a `.env` file with the following contents:
```shell
# For AWS deploys
PSOXY_URL=<url>
PSOXY_ROLE=<role ARN>
PSOXY_IMPERSONATE=<user email> # only for Google Workspace data sources
PSOXY_SAVE_TO_FILE=true
PSOXY_VERBOSE=true
```
```shell
# For GCP deploys
PSOXY_URL=<url>
PSOXY_TOKEN=<identity token>
PSOXY_IMPERSONATE=<user email> # only for Google Workspace data sources
PSOXY_SAVE_TO_FILE=true
PSOXY_VERBOSE=true
```
Then, depending on the data connection you want to test, run any of the following:
```shell
npm run test:gcal
npm run test:gdrive
npm run test:google-chat
npm run test:google-meet
npm run test:slack-discovery-api
npm run test:zoom
```
**Notes**
- The API endpoints these scripts call are described in `/data-sources/spec.js` and `../docs/example-api-calls`
- The `PSOXY_SAVE_TO_FILE=true` variable will store the result of each call in a JSON file named after the API endpoint they called + the timestamp; for example, a call to call to Google Calendar: `https://[psoxy_url]/calendar/v3/calendars/primary`, will result in a file under `calendar-v3-calendars-primary-[ISO8601].json` (under the `/responses` folder).
- The `PSOXY_VERBOSE=true` will output the HTTP headers used to make the requests in the console.

[AWS]: https://aws.amazon.com
[GCP]: https://cloud.google.com/
[Node.js]: https://nodejs.org/en/
[npm]: https://www.npmjs.com
[signed]: https://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html
[Google Calendar]: https://developers.google.com/calendar/api
[Zoom]: https://zoom.us
[Zoom API endpoint]: https://marketplace.zoom.us/docs/api-reference/zoom-api/methods/#operation/users