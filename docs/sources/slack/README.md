# Slack

There are several connectors available for Slack:

- [Slack Bulk Data file](slack-discovery-bulk/README.md) - if your org already has an on-prem replica of your Slack data, you can use this connector to import that in bulk to Worklytics.
- [Slack Analytics](slack-analytics/README.md) - recommended connector for organizations with access to the Slack Admin Analytics API. Provides member/channel analytics, message metadata, and engagement metrics via `admin.analytics.*` endpoints.
- [Slack via Discovery API](slack-discovery-api/README.md) - **deprecated**; use Slack Analytics instead if your org has access to the Admin Analytics API.
- [Slack AI Analytics Bulk](slack-ai-bulk/README.md) - for Slack AI Snapshot CSV data (MAU, 30/90-day look-backs, etc.)
