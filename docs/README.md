# PSOXY - A pseudonymizing DLP layer between Worklytics and your data

[![Latest Release](https://img.shields.io/github/v/release/Worklytics/psoxy)](https://github.com/Worklytics/psoxy/releases/latest)
![java build](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-java.yaml?label=java)
![terraform examples build](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-terraform-examples.yaml?label=terraform%20examples)

A serverless, pseudonymizing, DLP layer between Worklytics and the REST API of your data sources.

Psoxy replaces PII in your organization's data with hash tokens to enable Worklytics's analysis to
be performed on anonymized data which we cannot map back to any identifiable individual.

Psoxy is a pseudonymization service that acts as a Security / Compliance layer, which you can deploy
between your data sources (SaaS tool APIs, Cloud storage buckets, etc) and the tools that need to
access those sources.

Psoxy ensures more secure, granular data access than direct connections between your tools will
offer - and enforces access rules to fulfill your Compliance requirements.

Psoxy functions as API-level Data Loss Prevention layer (DLP), by blocking sensitive fields / values
/ endpoints that would otherwise be exposed when you connect a data sources API to a 3rd party
service. It can ensure that data which would otherwise be exposed to a 3rd party service, due to
granularity of source API models/permissions, is not accessed or transfered to the service.

Objectives:
  - **serverless** - we strive to minimize the moving pieces required to run psoxy at scale, keeping
     your attack surface small and operational complexity low. Furthermore, we define
     infrastructure-as-code to ease setup.
  - **transparent** - psoxy's source code is available to customers, to facilitate code review
     and white box penetration testing.
  - **simple** - psoxy's functionality will focus on performing secure authentication with the 3rd
     party API and then perform minimal transformation on the response (pseudonymization, field
     redaction) to ease code review and auditing of its behavior.

Psoxy may be hosted in [Google Cloud](gcp/getting-started.md) or [AWS](aws/getting-started.md).

## Data Flow

A Psoxy instances reside on your premises (in the cloud) and act as an intermediary between
Worklytics and the data source you wish to connect. In this role, the proxy performs the
authentication necessary to connect to the data source's API and then any required transformation
(such as pseudonymization or redaction) on the response.

Orchestration continues to be performed on the Worklytics side.

![proxy illustration](proxy-illustration.jpg)

Source API data may include PII such as:

```json
{
  "id": "1234567890",
  "name": "John Doe",
  "email": "john.doe@acme.com"
}
```

But Psoxy ensures Worklytics only sees:
```json
{
    "id": "t~A80SJXrbfawKpDRcddGnKI4QDKyjQI9KtjJZDb8FZ27UE_toS68FyWz7Y22fnQYLP91SHJ",
    "email": "p~SIoJOpeSgYF7YUPQ28IWZexVuHyN9A80SJXrbfawKpDRcddGnKI4QDKyjQI9KtjJZDb8FZ27UE_toS68FyWz7Y22fnQYLP91SHJGVwQiN3E@acme.com"
}
```
These pseudonyms leverage SHA-256 hashing / AES encryption, with salt/keys that are known only to
your organization and never transferred to Worklytics.

Psoxy enforces that Worklytics can only access API endpoints you've configured ([principle of least
privilege](https://en.wikipedia.org/wiki/Principle_of_least_privilege)) using HTTP methods you allow (eg, limit to `GET` to enforce read-only for RESTful APIs).

For data sources APIs which require keys/secrets for authentication, such values remain stored in
your premises and are never accessible to Worklytics.

You authorize your Worklytics tenant to access your proxy instance(s) via the IAM platform of your
cloud host.

Worklytics authenticates your tenant with your cloud host via [Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation).
This eliminates the need for any secrets to be exchanged between your organization and Worklytics,
or the use any API keys/certificates for Worklytics which you would need to rotate.

See also: [API Data Sanitization](configuration/api-data-sanitization.md)

## Supported Data Sources
As of March 2023, the following sources can be connected to Worklytics via psoxy.

Note: Some sources require specific licenses to transfer data via the APIs/endpoints used by
Worklytics, or impose some per API request costs for such transfers.

### Google Workspace (formerly GSuite)

For all of these, a Google Workspace Admin must authorize the Google OAuth client you
provision (with [provided terraform modules](https://github.com/Worklytics/psoxy/tree/main/infra/examples))
to access your organization's data. This requires a Domain-wide Delegation grant with a set of
scopes specific to each data source, via the Google Workspace Admin Console.

If you use our provided Terraform modules, specific instructions that you can pass to the Google
Workspace Admin will be output for you.

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Examples&nbsp;&nbsp;&nbsp;&nbsp;                                                                                                                                                                                                                                     | Scopes Needed                                                                                            |
|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Google Calendar                                                                                                                | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/calendar/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/calendar/calendar.yaml)                                     | `calendar.readonly`                                                                                                                                                                                      |
| Google Chat                                                                                                                    | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/google-chat/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/google-chat/google-chat.yaml)                            | `admin.reports.audit.readonly`                                                                                                                                                                           |
| Google Directory                                                                                                               | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/directory/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/directory/directory.yaml)                                  | `admin.directory.user.readonly admin.directory.domain.readonly admin.directory.group.readonly admin.directory.orgunit.readonly` |
| Google Drive                                                                                                                   | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gdrive/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gdrive/gdrive.yaml)                                           | `drive.metadata.readonly`                                                                                                                                                                                |
| GMail                                                                                                                          | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gmail/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gmail/gmail.yaml)                                              | `gmail.metadata`                                                                                                                                                                                         |
| Google Meet
| Gemini Bulk **alpha**                                                                                                          | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gemini-usage/example.csv)                                                                                                                                                         | n/a;  bulk export of Gemini logs |
| Gemini for Google Workspace **alpha**                                                                                          | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gemini-for-workspace/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/google-workspace/gemini-for-workspace/gemini-for-workspace.yaml) | `admin.reports.audit.readonly`                                                                                                                                                                           |


NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](https://github.com/Worklytics/psoxy/tree/main/infra/modules/worklytics-connector-specs).
Please refer to that module for a definitive list.

NOTE: 'Google Directory' connection is required prerequisite for all other Google Workspace
connectors.

NOTE: you may need to enable the various Google Workspace APIs within the GCP project in which you
provision the OAuth Clients. If you use our provided terraform modules, this is done automatically.

NOTE: the above OAuth scopes omit the `https://www.googleapis.com/auth/` prefix. See [OAuth 2.0 Scopes for Google APIs](https://developers.google.com/identity/protocols/oauth2/scopes) for details of scopes.

See details: [sources/google-workspace/README.md](sources/google-workspace/README.md)

### Microsoft 365

For all of these, a Microsoft 365 Admin (at minimum, a [Privileged Role Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#privileged-role-administrator))
must authorize the Azure Application you provision (with [provided terraform modules](infra/examples)) to access your Microsoft 365 tenant's data with the scopes listed
below. This is done via the Azure Portal (Active Directory).  If you use our provided Terraform
modules, specific instructions that you can pass to the Microsoft 365 Admin will be output for you.

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Examples &nbsp;&nbsp;                                                                                                                                                                                                                  | Application Scopes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|--------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Entra ID (former Active Directory)                                                                     | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/entra-id/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/entra-id/entra-id.yaml)             | [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`MailboxSettings.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailboxsettingsread)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Calendar                                                                                               | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/outlook-cal/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/outlook-cal/outlook-cal.yaml)    | [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`Calendars.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#calendarsread) [`MailboxSettings.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailboxsettingsread)                                                                                                                                                                                                                                       |
| Mail                                                                                                   | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/outlook-mail/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/outlook-mail/outlook-mail.yaml) | [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`Group.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`Mail.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailreadbasicall) [`MailboxSettings.Read`](https://learn.microsoft.com/en-us/graph/permissions-reference#mailboxsettingsread)                                                                                                                                                                                                                                                                                                                                                |
| Teams (**__beta__**)                                                                                   | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/msft-teams/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/msft-teams/msft-teams.yaml)       | [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`Team.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#teamreadbasicall) [`Channel.ReadBasic.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelreadbasicall) [`Chat.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#chatreadall) [`ChannelMessage.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelmessagereadall) [`CallRecords.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#channelmessagereadall) [`OnlineMeetings.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#onlinemeetingsreadall) |
| Copilot (** alpha **)                                                                                  | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/msft-copilot/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/microsoft-365/msft-copilot/msft-copilot.yaml) | [`User.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#userreadall) [`AiEnterpriseInteraction.Read.All`](https://learn.microsoft.com/en-us/graph/permissions-reference#aienterpriseinteractionreadall) |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs)./
Please refer to that module for a definitive list.

NOTE: usage of the Microsoft Teams APIs may be billable, depending on your Microsoft 365 licenses and level of Teams usage. Please review: [Payment models and licensing requirements for Microsoft Teams APIs](https://learn.microsoft.com/en-us/graph/teams-licenses)

See details: [sources/microsoft-365/README.md](sources/microsoft-365/README.md)

### Github

Check the documentation to use the right permissions and the right authentication flow per connector.

| Source                         | Examples                                                                                                                                                                                         | Permissions (read only)                                                                          |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| GitHub Copilot (**__alpha__**) | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/copilot/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/copilot/github-copilot.yaml)                               | Organization: `Administration`, `Members`, `GitHub Copilot Business`                                |
| GitHub - Enterprise Server     | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/enterprise-server/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/enterprise-server/github-enterprise-server.yaml) | Repository: `Contents, Issues, Metadata`,`Pull requests`,  Organization: `Administration`, `Members` |
| GitHub - GitHub                | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/github/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/github/github.yaml)                                         | Repository: `Contents, Issues, Metadata`,`Pull requests`, Organization: `Administration`, `Members` |
| GitHub - Non-Enterprise        | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/github-non-enterprise/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/github/github-non-enterprise/github-non-enterprise.yaml)                                          | Repository: `Contents, Issues, Metadata`,`Pull requests`, Organization: `Members`                   |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs)./
Please refer to that module for a definitive list.

See details: [sources/github/README.md](sources/github/README.md)

### Slack

| Source                  | Examples                                                                                                                                                                                                                                                                                                                                                                                              | Scope                                                                     |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Slack Discovery API     | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/slack/slack-discovery-api/example-api-responses) - [rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/slack/slack-discovery-api/discovery.yaml)                                                                                                                                                                     | `discovery:read`                                                          |
| Slack Discovery Bulk    | [data](https://github.com/Worklytics/psoxy/tree/main/docs/sources/slack/slack-discovery-bulk/example-bulk) - [discovery bulk rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/slack/slack-discovery-bulk/discovery-bulk.yaml),[discovery hierarchical rules](https://github.com/Worklytics/psoxy/tree/main/docs/sources/slack/slack-discovery-bulk/discovery-bulk-hierarchical.yaml) | N/A                                                                       |
| Slack AI Snapshot       | N/A                                                                                                                                                                                                                                                                                                                                                                                                   | N/A |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs)./
Please refer to that module for a definitive list.

See details: [sources/github/README.md](sources/github/README.md)

### Other Data Sources via  APIs

These sources will typically require some kind of "Admin" within the tool to create an API key or
client, grant the client access to your organization's data, and provide you with the API key/secret
which you must provide as a configuration value in your proxy deployment.

The API key/secret will be used to authenticate with the source's REST API and access the data.


| Source                    | Details + Examples                                                               | API Permissions / Scopes                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|---------------------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Asana                     | [sources/asana](sources/asana/README.md)                               | a [Service Account](https://asana.com/guide/help/premium/service-accounts) (provides full access to Workspace)                                                                                                                                                                                                                                                                                                                                                 |
| Jira Cloud                | [sources/atlassian/jira-cloud](sources/atlassian/jira/README.md)   | "Classic Scopes": `read:jira-user` `read:jira-work` "Granular Scopes": `read:group:jira` `read:user:jira`  "User Identity API" `read:account`                                                                                                                                                                                                                                                                                                                  |
| Jira Server / Data Center | [sources/atlassian/jira-server](sources/atlassian/jira/jira-server.md) | Personal Acccess Token on behalf of user with access to equivalent of above scopes for entire instance                                                                                                                                                                                                                                                                                                                                                         |
| Salesforce                | [sources/salesforce](sources/salesforce/README.md)                     | `api` `chatter_api` `refresh_token` `offline_access` `openid` `lightning` `content` `cdp_query_api`                                                                                                                                                                                                                                                                                                                                                            |                                                                                                       |
| Zoom                      | [sources/zoom](sources/zoom/README.md)                                 | `meeting:read:past_meeting:admin` `meeting:read:meeting:admin` `meeting:read:list_past_participants:admin` `meeting:read:list_past_instances:admin` `meeting:read:list_meetings:admin` `meeting:read:participant:admin` `meeting:read:summary:admin` `cloud_recording:read:list_user_recordings:admin` `report:read:list_meeting_participants:admin` `report:read:meeting:admin` `report:read:user:admin` `user:read:user:admin` `user:read:list_users:admin` `user:read:settings:admin` |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](https://github.com/Worklytics/psoxy/tree/main/infra/modules/worklytics-connector-specs).
Please refer to that module for a definitive list.

### Other Data Sources via Bulk Data Exports

Other data sources, such as Human Resource Information System (HRIS), Badge, or Survey data can be
exported to a CSV file. The "bulk" mode of the proxy can be used to pseudonymize these files by
copying/uploading the original to a cloud storage bucket (GCS, S3, etc), which will trigger the
proxy to sanitize the file and write  the result to a 2nd storage bucket, which you then grant
Worklytics access to read.

Alternatively, the proxy can be used as a command line tool to pseudonymize arbitrary CSV files
(eg, exports from your  HRIS), in a manner consistent with how a psoxy instance will pseudonymize
identifiers in a target REST API. This is REQUIRED if you want SaaS accounts to be linked with HRIS
data for analysis (eg, Worklytics will match email set in HRIS with email set in SaaS tool's account
so these must be pseudonymized using an equivalent algorithm and secret). See [`java/impl/cmd-line/`](https://github.com/Worklytics/psoxy/tree/main/java/impl/cmd-line)
 for details.

See also: [Bulk File Sanitization](configuration/bulk-file-sanitization.md)


| Source                   | Details + Examples |
|--------------------------|--------------------|
| Badge                    | [sources/badge](sources/badge/README.md) |
| HRIS                     | [sources/hris](sources/hris/README.md) |
| Miro AI Bulk **alpha**   | [sources/miro/miro-ai-bulk](sources/miro/miro-ai-bulk/README.md) |
| Slack AI Bulk **alpha**  | [sources/slack/slack-ai-bulk](sources/slack/slack-ai-bulk/README.md) |
| Slack Discovery Bulk     | [sources/slack/slack-discovery-bulk](sources/slack/slack-discovery-bulk/README.md) |
| Survey                   | [sources/survey](sources/survey/README.md) |

### Other Data Sources via Webhook Collection

Some data sources may support **webhooks** to send data to a URL endpoint, often in response to
a user-performed action.  These 'events' can be collected by psoxy instances in "webhook collector" mode.

On-prem/in-house-build data sources can be insturmented to produce webhooks, using the [Worklytics Work Events JS SDK](https://github.com/Worklytics/Work-Events-JS).

See also: [Webhook Collectors](development/alpha-features/webhook-collectors.md)

## Getting Started - Customers

### Host Platform and Data Sources

The prequisites and dependencies you will need for Psoxy are determined by:
   1. Where you will host psoxy? eg, Amazon Web Services (AWS), or Google Cloud Platform (GCP)
   2. Which data sources you will connect to? eg, Microsoft 365, Google Workspace, Zoom, etc, as
      defined in previous sections.

Once you've gathered that information, you can identify the required software and permissions in the
next section, and the best environment from which to deploy Psoxy.


### Prerequisites

At a high-level, you need 3 things:
  1. a cloud host platform account to which you will deploy Psoxy (eg, AWS account or GCP project)
  2. an environment on which you will run the deployment tools (usually your laptop)
  3. some way to authenticate that environment with your host platform as an entity with sufficient
     permissions to perform the deployment. (usually an AWS IAM Role or a GCP Service
     Account, which your personal AWS or Google user can assume).

You, or the IAM Role / GCP Service account you use to deploy Psoxy, usually does NOT need to be
authorized to access or manage your data sources directly. Data access permissions and steps to
grant those vary by data source and generally require action to be taken by the data source
administrator AFTER you have deployed Psoxy.

#### Required Software and Permissions

As of April 2025, Psoxy is implemented with Java 17 and built via Maven. The proxy infrastructure is
provisioned and the Psoxy code deployed using Terraform, relying on Azure, Google Cloud, and/or AWS
command line tools.

You will need all the following in your deployment environment (eg, your laptop):

| Tool                                         | Version              | Test Command          |
|----------------------------------------------|----------------------|-----------------------|
| [git](https://git-scm.com/)                  | 2.17+                | `git --version`       |
| [Maven](https://maven.apache.org/)           | 3.6+                 | `mvn -v`              |
| [Java JDK 17+](https://openjdk.org/install/) | 17, 21 (see notes) | `mvn -v \| grep Java` |
| [Terraform](https://www.terraform.io/)       | 1.6+, < 2.0          | `terraform version`   |

NOTE: as of Apr 8, 2024, although Java 24 has been released Maven 3.9.9 is not compatible with it. Maven
has fixed this, but has yet to release a version 3.9.10 or 4.0.x with the fix. Until then, we don't officially
support Java 24.

NOTE: we will support Java versions for duration of official support windows, in particular the
LTS versions. Minor versions, such as 18-20, 22-23 which are out of official support, may work but are not
routinely tested.

NOTE: Using `terraform` is not strictly necessary, but it is the only supported method. You may
provision your infrastructure via your host's CLI, web console, or another infrastructure provisioning
tool, but we don't offer documentation or support in doing so.  Adapting one of our
[terraform examples](https://github.com/Worklytics/psoxy/tree/main/infra/examples) or writing your own config that re-uses our
[modules](https://github.com/Worklytics/psoxy/tree/main/infra/modules) will simplify things greatly.

NOTE: from v0.4.59, we've relaxed Terraform version constraint on our modules to allow up to 1.9.x.
However, we are not officially supporting this, as we strive to maintain compatibility with both
OpenTofu and Terraform.

Depending on your Cloud Host / Data Sources, you will need:

<table data-full-width="true">
  <thead>
    <tr>
      <th>Condition</th>
      <th>Tool</th>
      <th>Test Command</th>
      <th>Roles / Permissions (Examples, YMMV)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>if deploying to AWS</td>
      <td>
        <a href="https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html">AWS CLI</a>
        2.2+
      </td>
      <td><code>aws --version</code></td>
      <td>
        <ul>
          <li>
            <a
              href="https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/IAMFullAccess$serviceLevelSummary">IAMFullAccess</a>
          </li>
          <li>
            <a
              href="https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonSSMFullAccess$serviceLevelSummary">AmazonSSMFullAccess</a>
          </li>
          <li>
            <a
              href="https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AWSLambda_FullAccess$serviceLevelSummary">AWSLambda_FullAccess</a>
          </li>
          <li>
            <a
              href="https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonS3FullAccess$serviceLevelSummary">AmazonS3FullAccess</a>
          </li>
          <li>
            <a
              href="https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/CloudWatchFullAccess$serviceLevelSummary">CloudWatchFullAccess</a>
          </li>
        </ul>
        <p>see <a href="aws/getting-started.md">aws/getting-started.md</a></p>
      </td>
    </tr>
    <tr>
      <td>if deploying to GCP</td>
      <td><a href="https://cloud.google.com/sdk/docs/install">Google Cloud CLI</a> 1.0+</td>
      <td><code>gcloud version</code></td>
      <td>
        <ul>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator">Service Account
              Creator</a>
          </li>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.admin">Cloud Functions
              Admin</a>
          </li>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#storage.admin">Cloud Storage Admin</a>
          </li>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin">Secret Manager Admin</a>
          </li>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin">Service Usage
              Admin</a>
          </li>
        </ul>
        <p>see <a href="gcp/getting-started.md">gcp/getting-started.md</a></p>
      </td>
    </tr>
    <tr>
      <td>if connecting to Microsoft 365</td>
      <td>
        <a href="https://docs.microsoft.com/en-us/cli/azure/install-azure-cli">Azure CLI</a> 2.29+
      </td>
      <td><code>az --version</code></td>
      <td>
        <a
          href="https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#cloud-application-administrator">Cloud
          Application Administrator</a>
      </td>
    </tr>
    <tr>
      <td>if connecting to Google Workspace</td>
      <td><a href="https://cloud.google.com/sdk/docs/install">Google Cloud CLI</a> 1.0+</td>
      <td><code>gcloud version</code></td>
      <td>
        <ul>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator">Service Account
              Creator</a>
          </li>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountKeyAdmin">Service Account
              Key Admin</a>
          </li>
          <li>
            <a href="https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin">Service Usage
              Admin</a>
          </li>
        </ul>
        <p>see <a href="sources/google-workspace/">sources/google-workspace/README.md</a></p>
      </td>
    </tr>
  </tbody>
</table>

For testing your psoxy instance, you will need:

| Tool                                                               | Version                           | Test Command      |
|--------------------------------------------------------------------|-----------------------------------|-------------------|
| [Node.js](https://nodejs.org/en/)                                  | 16+ (ideally, latest LTS version) | `node --version`  |
| [npm](https://www.npmjs.com/package/npm) (should come with `node`) | 8+                                | `npm --version`   |

NOTE: Node.js v16 is unmaintained since Oct 2023, so **we recommend a newer version: v20, v22, v24, etc ...**.
_Some Node.js versions (e.g. v21) may display warning messages when running the test scripts_.

We provide a script to check these prereqs, at [`tools/check-prereqs.sh`](https://github.com/Worklytics/psoxy/tree/main/tools/check-prereqs.sh).
That script has no dependencies itself, so should be able to run on any plain POSIX-compliant shell
(eg,`bash`, `zsh`, etc) that we'd expect you to find on most Linux, MacOS, or even Windows with
Subsystem for Linux (WSL) platforms.

```shell
# from the root of a clone of this repository

./tools/check-prereqs.sh
```

### Setup

  1. Choose the cloud platform you'll deploy to, and follow its 'Getting Started' guide:
       - [AWS](aws/getting-started.md)
       - [Google Cloud platform](gcp/getting-started.md)

  2. Based on that choice, pick from the example template repos below. Use your choosen option as a
     template to create a new GitHub repo, or if you're not using GitHub Cloud, create clone/fork of the choosen option in your source control
     system:
        - AWS - https://github.com/Worklytics/psoxy-example-aws
        - GCP - https://github.com/Worklytics/psoxy-example-gcp

     You will make changes to the files contained in this repo as appropriate for your use-case.
     These changes should be committed to a repo that is accessible to other members of your team
     who may need to support your Psoxy deployment in the future.

  3. Pick the location from which you will deploy (provision) the psoxy instance. This location will
     need the software prereqs defined in the previous section. Some suggestions:

        - your local machine; if you have the prereqs installed and can authenticate it with your
          host platform (AWS/GCP) as a sufficiently privileged user/role, this is a simple option
        - [Google Cloud Shell](https://cloud.google.com/shell/) - if you're using GCP and/or connecting to
          Google Workspace, this is option simplifies authentication. It [includes the prereqs above](https://cloud.google.com/shell/docs/how-cloud-shell-works#tools)
          EXCEPT aws/azure CLIs out-of-the-box.
        - [Terraform Cloud](guides/terraform-cloud.md) - this works, but adds complexity of
          authenticating it with you host platform (AWS/GCP)
        - Ubuntu Linux VM/Container - we provide some setup instructions covering [prereq installation](prereqs-ubuntu.md)
          for Ubuntu variants of Linux, and specific authentication help for:
          - [EC2](aws/getting-started.md)

  4. Follow the 'Setup' steps in the READMEs of those repos, ultimately running `terraform apply`
     to deploy your Psoxy instance(s).

  5. follow any `TODO` instructions produced by Terraform, such as:
     - provision API keys / make OAuth grants needed by each Data Connection
     - create the Data Connection from Worklytics to your psoxy instance (Terraform can provide
       `TODO` file with detailed steps for each)

  6. Various test commands are provided in local files, as the output of the Terraform; you may use
     these examples to validate the performance of the proxy. Please review the proxy behavior and
     adapt the rules as needed. Customers needing assistance adapting the proxy behavior for their
     needs can contact support@worklytics.co

## Component Status

| Component                | Status                                                                                                                                    |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Java                     | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-java.yaml)                            |
| Terraform Examples       | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-terraform-examples.yaml)              |
| Tools                    | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-tools.yaml)                           |
| Terraform Security Scan | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-terraform-sec-analysis-examples.yaml) |

Review [release notes in GitHub](https://github.com/Worklytics/psoxy/releases).

## Support

Psoxy is maintained by Worklytics, Co. Support as well as professional services to assist with
configuration and customization are available. Please contact
[sales@worklytics.co](mailto:sales@worklytics.co) for more information or visit
[www.worklytics.co](https://www.worklytics.co).
