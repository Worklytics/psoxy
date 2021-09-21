# psoxy
A serverless, pseudonymizing proxy to sit between Worklytics and the REST API of a 3rd-party data source.

Psoxy replaces PII in your organization's data with hash tokens to enable Worklytics's 
analysis to be performed on anonymized data which we cannot map back to any identifiable
individual.  


## Goals

1. **serverless** - we strive to minimize the moving pieces required to run psoxy at scale, keeping your attack surface small and operational complexity low. Furthermore, we define infrastructure-as-code to ease setup.
2. **multi-cloud support** - using [Spring Cloud Function](https://spring.io/projects/spring-cloud-function), we aim to provide support for the major cloud providers.
3. **transparent** - psoxy's source code will be available to customers, to facilitate
code review and white box penetration testing.
4. **simple** - psoxy's functionality will focus on performing secure authentication with the 3rd party API and then perform minimal transformation on the response (pseudonymization, field filtering). to ease code review and auditing of its behavior.


## Data Flow Diagram
The best way to illustrate how psoxy works is an illustration:

## Getting Started

  1. apply [terraform]() module found in [`infra`](/infra)
  2. create OAuth client / generate API key in each of your desired data sources (see below)
  3. set your API keys via Secret Manager
  4. create the Data Connection in Worklytics

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
