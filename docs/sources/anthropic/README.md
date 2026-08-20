# Anthropic

There are several connectors for Anthropic / Claude products. They use different APIs and cover different use cases — pick the one that matches what you need:

| Connector | Connector ID | API | When to use |
|---|---|---|---|
| [Claude](claude/README.md) | `claude` | [Compliance API](https://platform.claude.com/docs/en/manage-claude/compliance-api-access) | Chat conversations, messages, users, and audit/activity logs for compliance, security, or data-privacy use cases |
| [Claude Enterprise Analytics](claude-enterprise-analytics/README.md) | `claude-enterprise-analytics` | [Enterprise Analytics API](https://support.claude.com/en/articles/13703965-claude-enterprise-analytics-api-reference-guide) (Enterprise plan only) | Per-user usage/cost metrics across all Claude surfaces (chat, Claude Code, Office add-ins, Cowork) |
| [Claude Code](claude-code/README.md) | `claude-code` | [Admin API](https://platform.claude.com/docs/en/manage-claude/admin-api) / [Claude Code Analytics API](https://platform.claude.com/docs/en/manage-claude/claude-code-analytics-api) | Claude Code usage for non-Enterprise (or Admin API) orgs — sessions, tokens, commits, PRs, lines of code |
| [Claude Code Bulk](claude-code-bulk/README.md) | `claude-code-bulk` | Bulk CSV/NDJSON import | Same Claude Code usage events via file upload instead of the API; do not run concurrently with `claude-code` |

**Notes:**

- `claude` (Compliance API) and `claude-enterprise-analytics` are complementary: one is about *what people did* (chats, activities); the other is about *how much they used* Claude (Enterprise analytics).
- For Claude Code specifically: prefer `claude-enterprise-analytics` on an Enterprise plan if you want Claude Code usage alongside other Claude surfaces; use `claude-code` when you only need Claude Code via the Admin API (typical for non-Enterprise). Use `claude-code-bulk` when exporting usage as files rather than calling the API.
- Each connector uses a different Anthropic key type / creation URL (Compliance Access Key, Analytics API key, or Admin API key). Do not reuse keys across connectors.
