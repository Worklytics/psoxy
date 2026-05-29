# Data Sources

This section describes many of the pre-configured data sources that can be connected to Worklytics via Psoxy.

The table below enumerates the available connectors, provided via the `worklytics-connector-specs` Terraform module (see [infra/modules/worklytics-connector-specs](../../infra/modules/worklytics-connector-specs)).

To add a source, add its Connector ID to the `enabled_connectors` list in your `terraform.tfvars` file.

| Connector ID               | Data Source                                                                 | Type | Availability |
|----------------------------|-----------------------------------------------------------------------------|------|--------------|
| `asana`                    | [Asana](asana/README.md)                                                    | API  | GA           |
| `atlassian-organization` | [Atlassian Organization](atlassian/organization/README.md)                  | API  | BETA         |
| `azure-ad`                 | [Azure Active Directory](microsoft-365/entra-id/README.md)                  | API  | DEPRECATED   |
| `badge`                    | [Badge](badge/README.md)                                                    | Bulk | GA           |
| `chatgpt-enterprise`       | [ChatGPT Enterprise](chatgpt-enterprise/README.md)                          | API  | ALPHA        |
| `claude`                   | [Claude](anthropic/claude/README.md)                                        | API  | BETA         |
| `claude-code`              | [Claude Code](anthropic/claude-code/README.md)                              | API  | BETA         |
| `confluence-cloud`         | [Confluence Cloud](atlassian/confluence/README.md)                          | API  | BETA         |
| `cursor`                   | [Cursor](cursor/README.md)                                                  | API  | BETA         |
| `dropbox-business`         | [Dropbox Business](dropbox-business/README.md)                              | API  | DEPRECATED   |
| `gcal`                     | [Google Calendar](google-workspace/calendar/README.md)                      | API  | GA           |
| `gdirectory`               | [Google Directory](google-workspace/directory/README.md)                    | API  | GA           |
| `gdrive`                   | [Google Drive](google-workspace/gdrive/README.md)                           | API  | GA           |
| `gemini-in-workspace-apps` | [Google Gemini in Workspace Apps](google-workspace/gemini-in-workspace-apps/README.md) | API  | BETA         |
| `gemini-usage`             | [Gemini Usage](google-workspace/gemini-usage-bulk/README.md)                | Bulk | DEPRECATED   |
| `github`                   | [GitHub Enterprise](github/github/README.md)                                | API  | GA           |
| `github-copilot`           | [GitHub Copilot](github/copilot/README.md)                                  | API  | ALPHA        |
| `github-enterprise-server` | [GitHub Enterprise Server](github/enterprise-server/README.md)              | API  | GA           |
| `github-non-enterprise`    | [GitHub Free/Pro/Teams](github/github-non-enterprise/README.md)             | API  | GA           |
| `gitlab-cloud`             | [GitLab Cloud](gitlab/gitlab-cloud/README.md)                               | API  | BETA         |
| `gitlab-managed`           | [GitLab Self-Managed / Dedicated](gitlab/gitlab-managed/README.md)          | API  | BETA         |
| `glean`                    | [Glean](glean/README.md)                                                    | API  | BETA         |
| `gmail`                    | [Gmail](google-workspace/gmail/README.md)                                   | API  | GA           |
| `gong-metrics`             | [Gong Metrics](gong/gong-metrics/README.md)                                 | API  | BETA         |
| `google-chat`              | [Google Chat](google-workspace/google-chat/README.md)                       | API  | GA           |
| `google-meet`              | [Google Meet](google-workspace/meet/README.md)                              | API  | GA           |
| `hris`                     | [HRIS](hris/README.md)                                                      | Bulk | GA           |
| `jira-cloud`               | [Jira Cloud](atlassian/jira/README.md)                                      | API  | GA           |
| `jira-server`              | [Jira Server / Data Center](atlassian/jira/jira-server.md)                  | API  | GA           |
| `metrics`                  | [Metrics](metrics/README.md)                                                | Bulk | BETA         |
| `msft-copilot`             | [Microsoft 365 Copilot](microsoft-365/msft-copilot/README.md)               | API  | ALPHA        |
| `msft-entra-id`            | [Microsoft Entra ID](microsoft-365/entra-id/README.md)                      | API  | GA           |
| `msft-teams`               | [Microsoft Teams](microsoft-365/msft-teams/README.md)                       | API  | GA           |
| `outlook-cal`              | [Outlook Calendar](microsoft-365/outlook-cal/README.md)                     | API  | GA           |
| `outlook-mail`             | [Outlook Mail](microsoft-365/outlook-mail/README.md)                        | API  | GA           |
| `qualtrics`                | [Qualtrics](survey/README.md)                                               | Bulk | BETA         |
| `salesforce`               | [Salesforce](salesforce/README.md)                                          | API  | GA           |
| `slack-analytics`          | [Slack Analytics](slack/slack-analytics/README.md)                           | API  | ALPHA        |
| `slack-ai-analytics-bulk`  | [Slack AI Analytics Bulk](slack/slack-ai-bulk/README.md)                    | Bulk | ALPHA        |
| `slack-discovery-api`      | [Slack via Discovery API](slack/slack-discovery-api/README.md)              | API  | DEPRECATED   |
| `survey`                   | [Survey](survey/README.md)                                                  | Bulk | GA           |
| `windsurf`                 | [Windsurf](windsurf/README.md)                                              | API  | ALPHA        |
| `workdata-generic`         | [Workdata Generic](workdata-generic/README.md)                              | Bulk | BETA         |
| `zoom`                     | [Zoom](zoom/README.md)                                                      | API  | GA           |

The following additional bulk connectors are documented but configured via `custom_bulk_connectors` in Terraform rather than `enabled_connectors`:

| Connector ID / key   | Data Source                                                          | Type | Availability |
|----------------------|----------------------------------------------------------------------|------|--------------|
| `gong-bulk`          | [Gong Bulk](gong/gong-bulk/README.md)                                | Bulk | ALPHA        |
| `miro-ai-bulk`       | [Miro AI Bulk](miro/miro-ai-bulk/README.md)                          | Bulk | ALPHA        |
| `slack-discovery-bulk` | [Slack Bulk Exports](slack/slack-discovery-bulk/README.md)         | Bulk | GA           |
| `zoom-ai-metrics`    | [Zoom AI Metrics Snapshot](zoom/README.md#zoom-ai-metric-snapshot-bulk) | Bulk | ALPHA     |

From v0.4.58, you can confirm the availability of a connector by running the following command from the root of one of our examples:

```shell
./available-connectors
```

If you are using v0.4.58+ of our terraform modules, but don't have the above script in your example, try:

```shell
cp .terraform/modules/psoxy/tools/available-connectors.sh available-connectors
chmod +x available-connectors
```
