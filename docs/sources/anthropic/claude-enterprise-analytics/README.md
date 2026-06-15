# Claude Enterprise Analytics

**Connector ID:** `claude-enterprise-analytics`

**Availability:** Beta

Our Claude Enterprise Analytics data connector uses the [Enterprise Analytics API](https://support.claude.com/en/articles/13703965-claude-enterprise-analytics-api-reference-guide) (Enterprise plan only) to import per-user usage metrics across all Claude surfaces — chat, Claude Code, Office add-ins, and Cowork — into Worklytics.

## Data Collected

| Endpoint | Description | Required Scope |
|---|---|---|
| `GET /v1/organizations/analytics/users` | Per-user daily activity counters: chat conversations, Claude Code sessions/commits/PRs/lines of code, Office add-in sessions, web searches | `read:analytics` |
| `GET /v1/organizations/analytics/apps/chat/projects` | Per-project chat stats: creator identity, distinct user count, conversation count, message count | `read:analytics` |
| `GET /v1/organizations/analytics/user_usage_report` | Aggregated token consumption per user: uncached input, cache creation, cache read, output, total tokens, web search requests, request count | `read:analytics` |
| `GET /v1/organizations/analytics/user_cost_report` | Aggregated cost breakdown per user: amount, list amount, currency, request count | `read:analytics` |

### Privacy

User identifiers (`user_id`, `email`, `id`, `email_address`) are pseudonymized before data leaves your infrastructure. No message content is collected.

### Data Freshness

- **Activity / project endpoints** (`/users`, `/apps/chat/projects`): data available approximately 3 days after aggregation.
- **Cost / usage report endpoints** (`/user_usage_report`, `/user_cost_report`): typically available within 4 hours; values may be revised for up to 30 days as late events arrive. Each response includes a `data_refreshed_at` timestamp.

## Steps to Connect

See the [Claude Enterprise Analytics API Reference](https://support.claude.com/en/articles/13703965-claude-enterprise-analytics-api-reference-guide) for the latest. As of 2026 the following is required:

1. The **Primary Owner** of the Enterprise organization must sign in to [claude.ai/analytics/api-keys](https://claude.ai/analytics/api-keys).
2. Create a new API key with the **`read:analytics`** scope. You can create multiple keys per organization; rate limits apply at the organization level, not the key level.
3. Copy the key into the proxy as the `PSOXY_CLAUDE_ENTERPRISE_ANALYTICS_ADMIN_API_KEY` parameter value in your proxy's host platform.

> **Note:** The Analytics API key is separate from the Admin API key used by other Claude connectors. It requires the **Primary Owner** role (not just `admin`) and is created at a different URL.

## Examples

- [Example Rules](claude-enterprise-analytics.yaml)
- Example Data:
  - [users.json](example-api-responses/original/users.json) | [sanitized](example-api-responses/sanitized/users.json)
  - [apps_chat_projects.json](example-api-responses/original/apps_chat_projects.json) | [sanitized](example-api-responses/sanitized/apps_chat_projects.json)
  - [user_usage_report.json](example-api-responses/original/user_usage_report.json) | [sanitized](example-api-responses/sanitized/user_usage_report.json)
  - [user_cost_report.json](example-api-responses/original/user_cost_report.json) | [sanitized](example-api-responses/sanitized/user_cost_report.json)
