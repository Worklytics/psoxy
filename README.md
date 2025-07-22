# PSOXY - A pseudonymizing DLP layer between Worklytics and your data

[![Latest Release](https://img.shields.io/github/v/release/Worklytics/psoxy)](https://github.com/Worklytics/psoxy/releases/latest)
![java build](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-java.yaml?label=java)
![terraform examples build](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-terraform-examples.yaml?label=terraform%20examples)

A serverless, pseudonymizing, DLP layer between Worklytics and the REST API of your data sources.

Psoxy replaces PII in your organization's data with hash tokens to enable Worklytics's analysis to be performed on anonymized data which we cannot map back to any identifiable individual.

Psoxy is a pseudonymization service that acts as a Security / Compliance layer, which you can deploy between your data sources (SaaS tool APIs, Cloud storage buckets, etc) and the tools that need to access those sources.

Psoxy ensures more secure, granular data access than direct connections between your tools will offer - and enforces access rules to fulfill your Compliance requirements.

Psoxy functions as API-level Data Loss Prevention layer (DLP), by blocking sensitive fields / values / endpoints that would otherwise be exposed when you connect a data sources API to a 3rd party service. It can ensure that data which would otherwise be exposed to a 3rd party service, due to granularity of source API models/permissions, is not accessed or transferred to the service.

Objectives:
  - **serverless** - we strive to minimize the moving pieces required to run psoxy at scale, keeping your attack surface small and operational complexity low. Furthermore, we define infrastructure-as-code to ease setup.
  - **transparent** - psoxy's source code is available to customers, to facilitate code review and white box penetration testing.
  - **simple** - psoxy's functionality will focus on performing secure authentication with the 3rd party API and then perform minimal transformation on the response (pseudonymization, field redaction) to ease code review and auditing of its behavior.

## Documentation

For full documentation, visit [https://docs.worklytics.co/psoxy](https://docs.worklytics.co/psoxy).

For development purposes, latest docs are also accessible at GitHub [docs/](./docs/README.md).

## Support

Psoxy is maintained by Worklytics, Co.

Support, as well as professional services, to assist with configuration and customization are available. Please contact [sales@worklytics.co](mailto:sales@worklytics.co) for more information or visit [www.worklytics.co](https://www.worklytics.co).
