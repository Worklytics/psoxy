# psoxy
A serverless, pseudonymizing proxy to sit between Worklytics and the REST API of a 3rd-party data source.

Psoxy replaces PII in your organization's data with hash tokens to enable Worklytics's
analysis to be performed on anonymized data which we cannot map back to any identifiable
individual.


## Goals

### v1.0
1. **serverless** - we strive to minimize the moving pieces required to run psoxy at scale, keeping your attack surface small and operational complexity low. Furthermore, we define infrastructure-as-code to ease setup.

3. **transparent** - psoxy's source code will be available to customers, to facilitate
code review and white box penetration testing.
4. **simple** - psoxy's functionality will focus on performing secure authentication with the 3rd party API and then perform minimal transformation on the response (pseudonymization, field filtering). to ease code review and auditing of its behavior.

### Future
1. **multi-cloud support** - using [Spring Cloud Function](https://spring.io/projects/spring-cloud-function), we aim to provide support for the major cloud providers.
2. **incoming webhooks**


## Data Flow Diagram
The best way to illustrate how psoxy works is an illustration:

## Getting Started

  1. apply [terraform]() module found in [`infra`](/infra)
  2. create OAuth client / generate API key in each of your desired data sources (see below)
  3. set your API keys via Secret Manager
  4. create the Data Connection from Worklytics to your psoxy instance
      - authorize Worklytics to connect to your psoxy instance and, if necessary, provide authentication credentials.

## Supported Data Sources
Data source connectors will be marked with their stage of maturity:
  * *alpha* - preview, YMMV, still active development; only available to select pilot customers.
  * *beta* - available to all customers, but still under active development and we expect bugs in some environments.
  * *general availability* - excepted to be stable and reliable.

As of Sept 2021, the following sources can be connected via psoxy:
  * Google Workspace
    * Calendar *alpha*
    * Chat *alpha*
    * Drive *alpha*
    * GMail *alpha*
    * Meet *alpha*
  * Slack
    * eDiscovery *alpha*

## Development

Can run locally via IntelliJ + maven, using run config:
  - `psoxy [function:run...]` (located in `.idea/runConfigurations`)

Or from command line:

```shell
cd java
mvn function:run -Drun.functionTarget=co.worklytics.psoxy.HelloWorld
```

By default, that serves the function from http://localhost:8080.


### GMail Example

1.) run `terraform init` and `terraform apply` from `infra/dev-personal` to provision environment

#### Local
2.) run locally via IntelliJ run config

3.) execute the following to verify your proxy is working OK

```shell
curl -X GET \
http://localhost:8080/gmail/v1/users/me/messages \
-H "X-Psoxy-Service-Account-User: erik@worklytics.co"
```

```shell
curl -X GET \
http://localhost:8080/gmail/v1/users/me/messages/17c3b1911726ef3f\?format=metadata \
-H "X-Psoxy-Service-Account-User: erik@worklytics.co"
```

#### Cloud
2.) deploy to GCP using IntelliJ run config (setting `gcpProjectId` maven property to your project)

3.) grant yourself access (probably not needed if you have primitive role in project, like Owner or
Editor)
```shell
gcloud alpha functions add-iam-policy-binding psoxy-gmail --region=us-central1 --member=user:erik@worklytics.co --role=roles/cloudfunctions.invoker --project=psoxy-dev-erik
```

4.) invocation

```shell
curl -X GET \
https://us-central1-psoxy-dev-erik.cloudfunctions.net/psoxy-gmail/gmail/v1/users/me/messages/17c3b1911726ef3f\?format=metadata \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-Service-Account-User: erik@worklytics.co"
```
