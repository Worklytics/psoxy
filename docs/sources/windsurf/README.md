# Windsurf

**Connector ID:** `windsurf`

**Availability:** Deprecated — we are not supporting new Windsurf connections as of Aug 2026, given Windsurf's acquisition and rebrand as Devin Desktop.

Existing deployments should continue to work for as long as Windsurf/Devin expose and maintain the Analytics API, but given that transition we do not expect that to remain the case indefinitely and are not committing to ongoing support.

Our Windsurf data connector uses the Analytics API to import data about Users (accounts) and aggregate usage data  (metrics) to Worklytics. As of July 2025, this API requires an Enterprise SaaS subscription.

[https://docs.windsurf.com/windsurf/accounts/analytics-api#overview](https://docs.windsurf.com/windsurf/accounts/analytics-api#overview)

## Steps to Connect

See Windsurf's documentation for the latest, but as of July 10, 2025, you must create an Service Key with "Teams Read-only" permissions and fill that as a secret in your host platform. The Proxy will then this value as the `service_key` parameter when connecting to Windsurf's API.

1. An admin user must navigate to the "service key section" of the Settings page, and create a Service Key with a role that has the "Teams Read-only" permissions.

2. Copy the service key into the proxy, as `PSOXY_WINDSURF_SERVICE_KEY` parameter value in your proxy's host platform.
