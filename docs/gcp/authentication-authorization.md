# Authentication and Authorization in GCP Deployments of Psoxy

This page provides an overview of how psoxy authenticates and confirms authorization of clients (Worklytics tenants) to access data for GCP-hosted deployments.

For general overview of how Psoxy is authorized to access data sources, and authenticates when making API calls to those sources, see [API Mode Authentication and Authorization](../authentication-authorization.md).

## Authentication

As Worklytics tenants run inside GCP, they are implicitly authenticated by GCP. No secrets or keys need be exchanged between your Worklytics tenant and your Psoxy instance. GCP can verify the identity of requests from Worklytics to your instance, just as it does between any process and resource within GCP.

## Authorization

Invocations of your proxy instances are authorized by the IAM policies you define in GCP. For API connectors, you grant the Cloud Function Invoker role to your Worklytics tenant's GCP service account on the Cloud Function for your instance.

For the bulk data case, you grant the Storage Object Viewer role to your Worklytics tenant's GCP service account on the sanitized output bucket for your connector.

You can obtain the identity of your Worklytics tenant's GCP service account from the Worklytics portal.
