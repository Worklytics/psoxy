## Testing

By default, the Terraform examples provided by Worklytics install a NodeJS-based tool for testing
your proxy deployments.

Full documentation of the test tool is available [here](guides/psoxy-test-tool.md). And the code is
located in the `tools` directory of the [Psoxy repository](https://github.com/Worklytics/psoxy).

### Testing Pre-requisites

Wherever you run this test tool from, your AWS or GCloud CLI _must_ be authenticated as an entity
with permissions to invoke the Lambda functions / Cloud functions that you deployed for Psoxy.

If you're testing the bulk cases, the entity must be able to read/write to the cloud storage buckets
created for each of those bulk examples.

### Testing Locally when Terraform ran remotely (eg, Terraform Cloud, GitHub Actions, etc)

If you're running the Terraform examples in a different location from where you wish to run tests,
then you can install the tool alone:

1. Clone the Psoxy repo to your local machine:

```shell
git clone https://github.com/Worklytics/psoxy.git
```

2. From within that clone, install the test tool:

```shell
./tools/install-test-tool.sh
```

3. Get specific test commands for your deployment

    - If you set the `todos_as_outputs` variable to `true`, your Terraform apply run should contain
      `todo2` output variable with testing instructions.
    - If you set `todos_as_local_files` variable to `true`, your Terraform apply run should contain
      local files named `TODO 2 ...` with testing instructions.

    In both cases, you will need to replace the test tool path included there with the path to your
    installation.

4. Example commands of the primary testing tool: "Psoxy Test Calls"

```shell
# GCP deployment example:
node cli-call.js -u https://us-central1-acme.cloudfunctions.net/calendar/v3/calendars/primary -t <IDENTITY_TOKEN> -i user@acme.com
# AWS deployment example:
node cli-call.js -u https://acme.lambda-url.us-east-1.on.aws/v2/users -r arn:aws:iam::310635719553:role/PsoxyApiCaller
```

### Testing Deployments made without Terraform

If you used and approach other than Terraform, or did not directly use our Terraform examples, you
may not have the testing examples or the test tool installed on your machine.

In such a case, you can install the test tool manually by following steps 1+2 above, and then can
review the documentation on how to use it from your machine.
