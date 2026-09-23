# Claude Enterprise Analytics

**Connector ID:** `claude-enterprise-analytics`

**Availability:** Beta

Our Claude Enterprise Analytics data connector uses the [Enterprise Analytics API](https://support.claude.com/en/articles/13703965-claude-enterprise-analytics-api-reference-guide) (Enterprise plan only) to import per-user usage metrics across all Claude surfaces — chat, Claude Code, Office add-ins, and Cowork — into Worklytics. It also imports per-member spend limit data from the [Spend Limits API](https://platform.claude.com/docs/en/manage-claude/spend-limits-api), which shares the same Enterprise key and is likewise Enterprise-only.

**Proxy version:** the Spend Limits endpoints below are allow-listed starting in proxy version `0.6.9`.

## Data Collected

| Endpoint | Description | Required Scope |
|---|---|---|
| `GET /v1/organizations/analytics/users` | Per-user daily activity counters: chat conversations, Claude Code sessions/commits/PRs/lines of code, Office add-in sessions, web searches | `read:analytics` |
| `GET /v1/organizations/analytics/apps/chat/projects` | Per-project chat stats: creator identity, distinct user count, conversation count, message count | `read:analytics` |
| `GET /v1/organizations/analytics/user_usage_report` | Aggregated token consumption per user: uncached input, cache creation, cache read, output, total tokens, web search requests, request count | `read:analytics` |
| `GET /v1/organizations/analytics/user_cost_report` | Aggregated cost breakdown per user: amount, list amount, currency, request count | `read:analytics` |
| `GET /v1/organizations/spend_limits/effective` | Per-member effective spend limit, its source (user override, seat tier, group, or org default), and period-to-date spend | `read:spend_limits` |
| `GET /v1/organizations/spend_limits/{SPEND_LIMIT_ID}` | Single spend-limit record lookup by id (same shape as one row of `/effective`) | `read:spend_limits` |
| `GET /v1/organizations/spend_limit_increase_requests` | A member's request for a higher spend limit and how it was resolved (pending/approved/denied) | `read:spend_limits` |
| `GET /v1/organizations/spend_limit_increase_requests/{SPEND_LIMIT_INCREASE_REQUEST_ID}` | Single increase-request record lookup by id | `read:spend_limits` |

Only `GET` endpoints are allow-listed. Setting/clearing a spend limit override and approving/denying an increase request (`POST`/`DELETE`) are admin actions, not data reporting, and are intentionally not proxied.

`{SPEND_LIMIT_ID}` is a spend-limit id from `GET /v1/organizations/spend_limits/effective`. `{SPEND_LIMIT_INCREASE_REQUEST_ID}` is an increase-request id from `GET /v1/organizations/spend_limit_increase_requests`.

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
  - [spend_limits_effective.json](example-api-responses/original/spend_limits_effective.json) | [sanitized](example-api-responses/sanitized/spend_limits_effective.json)
  - [spend_limit_effective_single.json](example-api-responses/original/spend_limit_effective_single.json) | [sanitized](example-api-responses/sanitized/spend_limit_effective_single.json)
  - [spend_limit_increase_requests.json](example-api-responses/original/spend_limit_increase_requests.json) | [sanitized](example-api-responses/sanitized/spend_limit_increase_requests.json)
  - [spend_limit_increase_request_single.json](example-api-responses/original/spend_limit_increase_request_single.json) | [sanitized](example-api-responses/sanitized/spend_limit_increase_request_single.json)
