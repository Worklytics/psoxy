# Data Sources

This section describes many of the pre-configured data sources that can be connected to Worklytics
via Psoxy.

The table below enumerates the available connectors, provided via the `worklytics-connector-specs`
Terraform module (see [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs)).

To add a source, add its Connector ID to the `enabled_connectors` list in your `terraform.tfvars`
file.

| Connector ID               | Data Source                          | Type | Availability |
|----------------------------|--------------------------------------|------|--------------|
| `asana`                    | Asana                                | API  | GA           |
| `azure-ad`                 | Azure Active Directory               | API  | DEPRECATED   |
| `badge`                    | Badge                                | Bulk | GA           |
| `dropbox-business`         | Dropbox Business                     | API  | DEPRECATED   |
| `gcal`                     | Google Calendar                      | API  | GA           |
| `gdrive`                   | Google Drive                         | API  | GA           |
| `gdirectory`               | Google Directory                     | API  | GA           |
| `gemini-usage`            | Gemini Usage                         | Bulk | BETA         |
| `github`                   | GitHub                               | API  | GA           |
| `github-enterprise-server` | GitHub Enterprise Server             | API  | GA           |
| `github-non-enterprise`    | GitHub Non-Enterprise                | API  | GA           |
| `gmail`                    | Gmail                                | API  | GA           |
| `google-chat`              | Google Chat                          | API  | GA           |
| `google-meet`              | Google Meet                          | API  | GA           |
| `hris`                     | HR Information System (eg, Workday)  | Bulk | GA           |
| `jira-cloud`               | Jira Cloud                           | API  | GA           |
| `jira-server`              | Jira Server                          | API  | GA           |
| `outlook-cal`              | Outlook Calendar                     | API  | GA           |
| `outlook-mail`             | Outlook Mail                         | API  | GA           |
| `msft-teams`               | Microsoft Teams                      | API  | BETA         |
| `msft-entra-id`            | Microsoft Entra ID                   | API  | GA           |
| `qualtrics`                | Qualtrics (survey)                   | API  | GA           |
| `salesforce`               | Salesforce                           | API  | GA           |
| `slack-discovery-api`      | Slack Discovery API                  | API  | GA           |
| `survey`                   | Survey                               | Bulk | GA           |
| `zoom`                     | Zoom                                 | API  | GA           |

From v0.4.58, you can confirm the availability of a connector by running the following command from
the root of one of our examples:

```shell
./available-connectors
```

If your using v0.4.58+ of our terraform modules, but don't have the above script in your example, try
```shell
cp .terraform/modules/psoxy/tools/available-connectors.sh available-connectors
chmod +x available-connectors
```
