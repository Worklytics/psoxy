# API Mode Authentication and Authorization

There are two connection legs to consider with regard to authentication and authorization in API mode:
1. between Worklytics and the proxy (your host cloud)
2. between the proxy and the data source API

Eg, Worklytics initiates an API request to the proxy (1); which, after validating the request, forwards it to the data source API on behalf of Worklytics (2), adding its additional authentication information.

## Worklytics to Proxy (1)

Worklytics is **authorized** to access your proxy instance via an Identity and Access Management (IAM) policy which you must configure in your host platform. The exact details vary by cloud provider:
  - [AWS](aws/authentication-authorization.md)
  - [GCP](gcp/authentication-authorization.md)

Worklytics **authenticates** in all cases via Workload Identity Federation; as your Worklytics tenant is running natively in the cloud, it can leverage the cloud provider's native IAM service to establish identity which can be asserted to other services in the cloud.

## Proxy to Data Source API (2)

Although exact details vary by data source, most utilize some form of [OAuth 2.0](https://oauth.net/2/) for authorization and authentication.

A data source admin (eg, a Google Workspace admin) must **authorize** the proxy to access the data source via the data source's admin console. This typically involves creating a new OAuth 2.0 client and granting that client a set of [oauth scopes](https://oauth.net/2/scope/) required to support the API calls that will be made on behalf of Worklytics.  A detailed list of scopes required for each data source is specified in the documentation of each connector.

See https://docs.worklytics.co/psoxy#supported-data-sources

The proxy **authenticates** itself for calls to the data source using one of the supported OAuth 2.0 mechanisms, see [https://oauth.net/2/client-authentication/]. Most commonly, these are [Client Credentials](https://oauth.net/2/grant-types/client-credentials/) or [Workload Identity Federation](https://learn.microsoft.com/en-us/entra/workload-id/workload-identity-federation).

In particular, a quick overview for common sources:
  - Microsoft 365 sources authenticate via Workload Identity Federation
  - Google Workspace sources authenticate via Client Credentials (a GCP Service Account key)
  - GitHub authenticates via Client Credentials (a GitHub App client id + key)
  - Jira authenticates via Client Credentials (a Jira App client id + secret)
  - Slack authenticates via Client Credentials (a Slack App token)
  - Salesforce authenticates via Client Credentials (a Salesforce App client id + secret)
  - Zoom authenticates via Client Credentials (a Zoom App client id + secret)

In all cases relying on secrets (a key, client secret, token, etc) to authenticate, these values are stored in the secret store implementation of your Host cloud provider (eg, GCP Secret Manager) and **never** passed to or accessed by Worklytics.  Worklytics has no means to directly connect to any of your data sources.
