# Slack

There are several connectors available for Slack:

- [Slack Bulk Data file](slack-discovery-bulk/README.md) - if your org already has an on-prem replica of your Slack data, you can use this connector to import that in bulk to Worklytics.
- [Slack via Discovery API](slack-discovery-api/README.md) - Required for DMs and multi-person DMs (MPDMs), which are not available via the Slack Analytics API. Access requires Enterprise Grid, an eligible Slack subscription, and explicit approval from your Slack account team to enable the `discovery:read` scope; using Discovery API for analytics is not an officially documented Slack use case, and Worklytics cannot warrant or guarantee API availability. As of May 2025, Slack is also restricting rate limits on new Discovery API clients.
- [Slack Analytics](slack-analytics/README.md) - if your org has access to the Slack Admin Analytics API, you can use this connector for member and public-channel analytics, message metadata, and engagement metrics via `admin.analytics.*` endpoints.
- [Slack AI Analytics Bulk](slack-ai-bulk/README.md) - for Slack AI Snapshot CSV data (MAU, 30/90-day look-backs, etc.)
