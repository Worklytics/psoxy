# AWS - Development

## Prereqs

Required:

- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)

Optional:

- AWS SAM CLI ([macOS](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install-mac.html)) for local testing, if desired
- [awscurl](https://github.com/okigan/awscurl) for direct testing of deployed AWS lambda from a terminal

## Build

Maven build produces a zip file.

1. Build core library
2. From `java/impl/aws/`:

```shell
mvn clean package
```

## Run Locally

Locally, you can test function's behavior from invocation on a JSON payload (but not how the API gateway will map HTTP requests to that JSON payload):

[https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-using-invoke.html](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-using-invoke.html)

## Deploy to AWS

We recommend deploying your Psoxy code into AWS using the terraform modules found in [`infra/modules/`](../../infra/modules/] for AWS. These modules both provision the required AWS infrastructure, as well as deploying the built binaries for Psoxy as lambdas in the target account.

Example configurations using those modules can be found in [`infra/examples/](../../infra/examples).

API connectors are invoked through the Lambda function URL. API Gateway remains optional. The dashed Bedrock path is an optional **beta**.

```mermaid
flowchart TB
    worklytics["Worklytics"]
    source["Third-party data source"]

    subgraph customer["Provisioned in the customer AWS account"]
        apigw["API Gateway<br/>optional"]
        lambda["Lambda<br/>validates the call, authenticates to the source, redacts and pseudonymizes the response"]
        params["Parameter Store<br/>configuration and source API credentials"]
        bedrock["Amazon Bedrock<br/>optional beta"]
        params -.-> lambda
        lambda -.->|"matched source text"| bedrock
        bedrock -.->|"derived metadata"| lambda
    end

    worklytics -->|"function URL<br/>workload identity"| lambda
    worklytics -.-> apigw
    apigw -.-> lambda
    lambda -->|"authenticated request"| source
    source -->|"API response"| lambda
    lambda -->|"sanitized response"| worklytics

    classDef beta fill:#fff7e6,stroke:#c47d00,stroke-dasharray: 5 5
    class bedrock beta
```

Bedrock runs only when `enable_gen_metadata = true` and a custom rule uses `!<classify>` or `!<genMetadata>`. Salt and source credentials stay in Parameter Store. See [genMetadata](../development/alpha-features/gen-metadata-augment.md).

See [`infra/modules/aws/`](../../infra/modules/aws/) for more information.
