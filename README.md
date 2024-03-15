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

Psoxy may be hosted in [Google Cloud ](docs/gcp/development.md) or [AWS](docs/aws/getting-started.md).

## Data Flow

A Psoxy instances reside on your premises (in the cloud) and act as an intermediary between
Worklytics and the data source you wish to connect.  In this role, the proxy performs the
authentication necessary to connect to the data source's API and then any required transformation
(such as pseudonymization or redaction) on the response.

Orchestration continues to be performed on the Worklytics side.

![proxy illustration](docs/proxy-illustration.jpg)

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

Psoxy enforces that Worklytics can only access API endpoints you've configured (principle of least
privilege) using HTTP methods you allow (eg, limit to `GET` to enforce read-only for RESTful APIs).

For data sources APIs which require keys/secrets for authentication, such values remain stored in
your premises and are never accessible to Worklytics.

You authorize your Worklytics tenant to access your proxy instance(s) via the IAM platform of your
cloud host.

Worklytics authenticates your tenant with your cloud host via [Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation).
This eliminates the need for any secrets to be exchanged between your organization and Worklytics,
or the use any API keys/certificates for Worklytics which you would need to rotate.

See also: [API Data Sanitization](docs/api-data-sanitization.md)

## Supported Data Sources
As of March 2023, the following sources can be connected to Worklytics via psoxy.

Note: Some sources require specific licenses to transfer data via the APIs/endpoints used by
Worklytics, or impose some per API request costs for such transfers.

### Google Workspace (formerly GSuite)

For all of these, a Google Workspace Admin must authorize the Google OAuth client you
provision (with [provided terraform modules](infra/examples)) to access your organization's data. This requires a
Domain-wide Delegation grant with a set of scopes specific to each data source, via the Google
Workspace Admin Console.

If you use our provided Terraform modules, specific instructions that you can pass to the Google
Workspace Admin will be output for you.

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Examples&nbsp;&nbsp;&nbsp;&nbsp;                                                                                                          | Scopes Needed                                                                                            |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Google Calendar                | [data](docs/sources/google-workspace/calendar/example-api-responses) - [rules](docs/sources/google-workspace/calendar/calendar.yaml)      | `calendar.readonly`                                                                                                                                                                                       |
| Google Chat                    | [data](docs/sources/google-workspace/google-chat/example-api-responses) - [rules](docs/sources/google-workspace/google-chat/google-chat.yaml) | `admin.reports.audit.readonly`                                                                                                                                                                            |
| Google Directory               | [data](docs/sources/google-workspace/directory/example-api-responses) - [rules](docs/sources/google-workspace/directory/directory.yaml)   | `admin.directory.user.readonly admin.directory.user.alias.readonly admin.directory.domain.readonly admin.directory.group.readonly admin.directory.group.member.readonly admin.directory.orgunit.readonly` |
| Google Drive                   | [data](docs/sources/google-workspace/gdrive/example-api-responses) - [rules](docs/sources/google-workspace/gdrive/gdrive.yaml)            | `drive.metadata.readonly`                                                                                                                                                                                 |
| GMail                          | [data](docs/sources/google-workspace/gmail/example-api-responses) - [rules](docs/sources/google-workspace/gmail/gmail.yaml)               | `gmail.metadata`                                                                                                                                                                                          |
| Google Meet                    | [data](docs/sources/google-workspace/meet/example-api-responses) - [rules](docs/sources/google-workspace/meet/meet.yaml)                  | `admin.reports.audit.readonly`                                                                                                                                                                            |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs).
Please refer to that module for a definitive list.

NOTE: 'Google Directory' connection is required prerequisite for all other Google Workspace
connectors.

NOTE: you may need to enable the various Google Workspace APIs within the GCP project in which you
provision the OAuth Clients. If you use our provided terraform modules, this is done automatically.

NOTE: the above OAuth scopes omit the `https://www.googleapis.com/auth/` prefix. See [OAuth 2.0 Scopes for Google APIs](https://developers.google.com/identity/protocols/oauth2/scopes) for details of scopes.

See details: [docs/sources/google-workspace/readme.md](docs/sources/google-workspace/google-workspace.md)

### Microsoft 365

For all of these, a Microsoft 365 Admin (at minimum, a [Privileged Role Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#privileged-role-administrator))
must authorize the Azure Application you provision (with [provided terraform modules](infra/examples)) to access your Microsoft 365 tenant's data with the scopes listed
below. This is done via the Azure Portal (Active Directory).  If you use our provided Terraform
modules, specific instructions that you can pass to the Microsoft 365 Admin will be output for you.

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Examples &nbsp;&nbsp;                                                                                                        | Application Scopes                                                                                                                                |
|--------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| Active Directory                                                                                       | [data](docs/sources/microsoft-365/directory/example-api-responses) - [rules](docs/sources/microsoft-365/directory/directory.yaml)          | `User.Read.All` `Group.Read.All`                                                                                                                  |
| Calendar                                                                                               | [data](docs/sources/microsoft-365/outlook-cal/example-api-responses) - [rules](docs/sources/microsoft-365/outlook-cal/outlook-cal.yaml)    | `User.Read.All` `Group.Read.All` `OnlineMeetings.Read.All` `Calendars.Read` `MailboxSettings.Read`                                                |
| Mail                                                                                                   | [data](docs/sources/microsoft-365/outlook-mail/example-api-responses) - [rules](docs/sources/microsoft-365/outlook-mail/outlook-mail.yaml) | `User.Read.All` `Group.Read.All`  `Mail.ReadBasic.All` `MailboxSettings.Read`                                                                     |
| Teams (**__beta__**)                                                                                   | [data](docs/sources/microsoft-365/msft-teams/example-api-responses) - [rules](docs/sources/microsoft-365/msft-teams/msft-teams.yaml)| `User.Read.All` `Team.ReadBasic.All` `Channel.ReadBasic.All` `Chat.Read.All` `ChannelMessage.Read.All` `CallRecords.Read.All` `OnlineMeetings.Read.All`  |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs)./
Please refer to that module for a definitive list.

NOTE: usage of the Microsoft Teams APIs may be billable, depending on your Microsoft 365 licenses and level of Teams usage. Please review: [Payment models and licensing requirements for Microsoft Teams APIs](https://learn.microsoft.com/en-us/graph/teams-licenses)

See details: [docs/sources/microsoft-365/readme.md](docs/sources/microsoft-365/README.md)

### Other Data Sources via REST APIs

These sources will typically require some kind of "Admin" within the tool to create an API key or
client, grant the client access to your organization's data, and provide you with the API key/secret
which you must provide as a configuration value in your proxy deployment.

The API key/secret will be used to authenticate with the source's REST API and access the data.


| Source                    | Details + Examples                                                               | API Permissions / Scopes                                                                                                                      |
|---------------------------|----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| Asana                     | [docs/sources/asana](docs/sources/asana/README.md)                               | a [Service Account](https://asana.com/guide/help/premium/service-accounts) (provides full access to Workspace)                                |
| GitHub                    | [docs/sources/github](docs/sources/github/README.md)                             | **Read Only** permissions for: <br/>Repository: Contents, Issues, Metadata, Pull requests<br/>Organization: Administration, Members           |
| Jira Cloud                | [docs/sources/atlassian/jira-cloud](docs/sources/atlassian/jira/README.md)   | "Classic Scopes": `read:jira-user` `read:jira-work` "Granular Scopes": `read:group:jira` `read:user:jira`  "User Identity API" `read:account` |
| Jira Server / Data Center | [docs/sources/atlassian/jira-server](docs/sources/atlassian/jira/jira-server.md) | Personal Acccess Token on behalf of user with access to equivalent of above scopes for entire instance                                        |
| Salesforce                | [docs/sources/salesforce](docs/sources/salesforce/README.md)                     | `api` `chatter_api` `refresh_token` `offline_access` `openid` `lightning` `content` `cdp_query_api`                                           |                                                                                                       |
| Slack                     | [docs/sources/slack](docs/sources/slack/README.md)                               | `discovery:read`                                                                                                                              |
| Zoom                      | [docs/sources/zoom](docs/sources/zoom/README.md)                                 | `user:read:admin` `meeting:read:admin` `report:read:admin`                                                                                    |


NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs).
Please refer to that module for a definitive list.

### Other Data Sources without REST APIs

Other data sources, such as Human Resource Information System (HRIS), Badge, or Survey data can be
exported to a CSV file. The "bulk" mode of the proxy can be used to pseudonymize these files by
copying/uploading the original to a cloud storage bucket (GCS, S3, etc), which will trigger the
proxy to sanitize the file and write  the result to a 2nd storage bucket, which you then grant
Worklytics access to read.

Alternatively, the proxy can be used as a command line tool to pseudonymize arbitrary CSV files
(eg, exports from your  HRIS), in a manner consistent with how a psoxy instance will pseudonymize
identifiers in a target REST API. This is REQUIRED if you want SaaS accounts to be linked with HRIS
data for analysis (eg, Worklytics will match email set in HRIS with email set in SaaS tool's account
so these must be pseudonymized using an equivalent algorithm and secret). See [`java/impl/cmd-line/`](/java/impl/cmd-line)
 for details.

See also: [Bulk File Sanitization](docs/bulk-file-sanitization.md)

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

As of Feb 2023, Psoxy is implemented with Java 11 and built via Maven. The proxy infrastructure is
provisioned and the Psoxy code deployed using Terraform, relying on Azure, Google Cloud, and/or AWS
command line tools.

You will need all the following in your deployment environment (eg, your laptop):

| Tool                                            | Version                | Test Command              |
|-------------------------------------------------|------------------------|---------------------------|
| [git](https://git-scm.com/)                     | 2.17+                  | `git --version`           |
| [Maven](https://maven.apache.org/)              | 3.6+                   | `mvn -v`                 |
| [Java JDK 11+](https://openjdk.org/install/) | 11, 17, 21 (see notes) | `mvn -v &#124; grep Java` |
| [Terraform](https://www.terraform.io/)          | 1.3.x, <= 1.6          | `terraform version`       |

NOTE: we will support Java versions for duration of official support windows, in particular the
LTS versions. As of Nov 2023, we  still support java 11 but may end this at any time. Minor
versions, such as 12-16, and 18-20, which are out of official support, may work but are not
routinely tested.

NOTE: Using `terraform` is not strictly necessary, but it is the only supported method. You may
provision your infrastructure via your host's CLI, web console, or another infrastructure provisioning
tool, but we don't offer documentation or support in doing so.  Adapting one of our
[terraform examples](infra/examples) or writing your own config that re-uses our
[modules](infra/modules) will simplify things greatly.

NOTE: Refrain to use Terraform versions 1.4.x that are < v1.4.3. We've seen bugs.

Depending on your Cloud Host / Data Sources, you will need:

| Condition                         | Tool                                                                                       | Version | Test Command     | Roles / Permissions (Examples, YMMV)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|-----------------------------------|--------------------------------------------------------------------------------------------|---------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| if deploying to AWS               | [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)   | 2.2+    | `aws --version`  | <ul><li>[IAMFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/IAMFullAccess$serviceLevelSummary)</li><li>[AmazonSSMFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonSSMFullAccess$serviceLevelSummary)</li><li>[AWSLambda_FullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AWSLambda_FullAccess$serviceLevelSummary)</li><li>[AmazonS3FullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/AmazonS3FullAccess$serviceLevelSummary)</li><li>[CloudWatchFullAccess](https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/policies/arn:aws:iam::aws:policy/CloudWatchFullAccess$serviceLevelSummary)</li></ul><br/>see [docs/aws/getting-started.md](docs/aws/getting-started.md) |
| if deploying to GCP               | [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)                              | 1.0+    | `gcloud version` | <ul><li>[Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator)</li><li>[Cloud Functions Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudfunctions.admin)</li><li>[Cloud Storage Admin](https://cloud.google.com/iam/docs/understanding-roles#storage.admin)</li><li>[Secret Manager Admin](https://cloud.google.com/iam/docs/understanding-roles#secretmanager.admin)</li><li>[Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin)</li></ul><br/>see [docs/gcp/getting-started.md](docs/gcp/getting-started.md)                                                                                                                                                                                                                   |
| if connecting to Microsoft 365    | [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli)                  | 2.29+   | `az --version`   | [Cloud Application Administrator](https://learn.microsoft.com/en-us/azure/active-directory/roles/permissions-reference#cloud-application-administrator)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| if connecting to Google Workspace | [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)                              | 1.0+    | `gcloud version` | <ul><li>[Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator)</li><li>[Service Account Key Admin](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountKeyAdmin)</li><li>[Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin)</li></ul><br/>see [docs/sources/google-workspace/google-workspace.md](docs/sources/google-workspace/google-workspace.md)                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |


For testing your psoxy instance, you will need:

| Tool                                                               | Version | Test Command      |
|--------------------------------------------------------------------|---------|-------------------|
| [Node.js](https://nodejs.org/en/)                                  | 16+     | `node --version`  |
| [npm](https://www.npmjs.com/package/npm) (should come with `node`) | 8+      | `npm --version`   |

NOTE: NodeJS 16 is unmaintained since Oct 2023, so we recommend newer version; but in theory should
work.

We provide a script to check these prereqs, at [`tools/check-prereqs.sh`](tools/check-prereqs.sh).
That script has no dependencies itself, so should be able to run on any plain POSIX-compliant shell
(eg,`bash`, `zsh`, etc) that we'd expect you to find on most Linux, MacOS, or even Windows with
Subsystem for Linux (WSL) platforms.

```shell
# from the root of a clone of this repository

./tools/check-prereqs.sh
```

### Setup

  1. Choose the cloud platform you'll deploy to, and follow its 'Getting Started' guide:
       - [AWS](docs/aws/getting-started.md)
       - [Google Cloud platform](docs/gcp/getting-started.md)

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
        - [Terraform Cloud](docs/terraform-cloud.md) - this works, but adds complexity of
          authenticating it with you host platform (AWS/GCP)
        - Ubuntu Linux VM/Container - we provide some setup instructions covering [prereq installation](docs/prereqs-ubuntu.md)
          for Ubuntu variants of Linux, and specific authentication help for:
          - [EC2](docs/aws/getting-started.md)

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

| Component                | Status                                                                                                                       |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Java                     | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-java.yaml)               |
| Terraform Examples       | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-terraform.yaml)          |
| Tools                    | ![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-tools.yaml)              |


Review [release notes in GitHub](https://github.com/Worklytics/psoxy/releases).

## Support

Psoxy is maintained by Worklytics, Co. Support as well as professional services to assist with
configuration and customization are available. Please contact
[sales@worklytics.co](mailto:sales@worklytics.co) for more information or visit
[www.worklytics.co](https://www.worklytics.co).
