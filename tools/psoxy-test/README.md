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
node cli.js -u https://us-central1-acme.cloudfunctions.net/calendar/v3/calendars/primary -t <IDENTITY_TOKEN> -i user@acme.com
```
Zoom example:
```shell
node cli.js -u https://us-central1-acme.cloudfunctions.net/v2/users -t <IDENTITY_TOKEN>
```

(*) You can obtain it by running `gcloud auth print-identity-token` (using Google Cloud SDK)

## Testing all endpoints for a given data source

The `-d, --data-source` option of our CLI script allows you to test all the endpoints for a given data source (available data sources are listed in the script's help: `-h` option). 
The only difference with the previous examples is that the `-u, --url` option has to be the URL of the deploy **without** the corresponding API path of the data source:

```shell
# Zoom example for AWS deploys, instead of:
# node cli.js -u https://acme.lambda-url.us-east-1.on.aws/v2/users -r <role>
# use:
node cli.js -u https://acme.lambda-url.us-east-1.on.aws -r <ROLE> -d zoom
```
```shell
# Zoom example for GCP deploys, instead of:
# node cli.js -u https://us-central1-acme.cloudfunctions.net/v2/users -t <ROLE>
# use:
node cli.js -u https://acme.lambda-url.us-east-1.on.aws -t <IDENTITY_TOKEN> -d zoom
```

Notice how the URL changes, and any other option the Psoxy may need doesn't.

[AWS]: https://aws.amazon.com
[GCP]: https://cloud.google.com/
[Node.js]: https://nodejs.org/en/
[npm]: https://www.npmjs.com
[signed]: https://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html
[Google Calendar]: https://developers.google.com/calendar/api
[Zoom]: https://zoom.us
[Zoom API endpoint]: https://marketplace.zoom.us/docs/api-reference/zoom-api/methods/#operation/users