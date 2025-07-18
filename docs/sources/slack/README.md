# Slack

There are several connectors available for Slack:

- [Slack Bulk Data file](slack-discovery-bulk/README.md) - if your org already has an on-prem replica of your Slack data, you can use this connector to import that in bulk to Worklytics.
- [Slack via Discovery API](slack-discovery-api/README.md) - if your org has access to the Slack Discovery API with sufficiently high rate limits, you can use this connector to transfer a sanitized copy of your Slack data to Worklytics.  As of May 2025, Slack is restricting creation of new Discovery API clients. You may need to contact your Slack account representative to ensure your organization can use this method to obtain a copy of your Slack data.
- [Slack Analytics](slack-analytics/README.md) - if your org has access to the Slack Analytics API, you can use this connector to transfer a sanitized copy of your Slack data to Worklytics.
- [Slack AI Snapshot Bulk](slack-ai-bulk/README.md) - for Slack AI Snapshot data
