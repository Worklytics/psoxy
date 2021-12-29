# AWS - Getting Started

## Prereqs

  - [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
  - AWS SAM CLI ([macOS](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install-mac.html))
  -

## Build
Maven build produces a zip file.

  1. Build core library
  2. From `java/impl/aws/`:
```shell
mvn clean package
```

## Run Locally


https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-using-invoke.html

## Run Tests

## Deploy

```shell
aws lambda update-function-code --function-name my-function --zip-file fileb://my-function.zip
```
