# psoxy

[![Latest Release](https://img.shields.io/github/v/release/Worklytics/psoxy)](https://github.com/Worklytics/psoxy/releases/latest)
![java build](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-java.yaml?label=java)
![terraform examples build](https://img.shields.io/github/actions/workflow/status/Worklytics/psoxy/ci-terraform-examples.yaml?label=terraform%20examples)

A serverless, pseudonymizing proxy to sit between Worklytics and the REST API of a 3rd-party data
source.

Psoxy replaces PII in your organization's data with hash tokens to enable Worklytics's analysis to
be performed on anonymized data which we cannot map back to any identifiable individual.

It is intended to be a simple, serverless, transparent solution to provide more granular access to
data source APIs.
  - **serverless** - we strive to minimize the moving pieces required to run psoxy at scale, keeping
     your attack surface small and operational complexity low. Furthermore, we define
     infrastructure-as-code to ease setup.
  - **transparent** - psoxy's source code is available to customers, to facilitate code review
     and white box penetration testing.
  - **simple** - psoxy's functionality will focus on performing secure authentication with the 3rd
     party API and then perform minimal transformation on the response (pseudonymization, field
     redcation). to ease code review and auditing of its behavior.

Psoxy may be hosted in [Google Cloud ](docs/gcp/development.md) or [AWS](docs/aws/getting-started.md).

## Data Flow

A Psoxy instances reside on your premises (in the cloud) and act as an intermediary between
Worklytics and the data source you wish to connect.  In this role, the proxy performs the
authentication necessary to connect to the data source's API and then any required transformation
(such as pseudonymization or redaction) on the response.

Orchestration continues to be performed on the Worklytics-side.

![proxy illustration](docs/proxy-illustration.jpg)

## Supported Data Sources
As of March 2023, the following sources can be connected to Worklytics via psoxy:

### Google Workspace (formerly GSuite)

For all of these, a Google Workspace Admin must authorize the Google OAuth client you
provision (with [provided terraform modules](infra/examples)) to access your organization's data. This requires a
Domain-wide Delegation grant with a set of scopes specific to each data source, via the Google
Workspace Admin Console.

If you use our provided Terraform modules, specific instructions that you can pass to the Google
Workspace Admin will be output for you.

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Examples&nbsp;&nbsp;&nbsp;&nbsp;                                                                                                        | Scopes Needed                                                                                                                                                                                                                                     |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Google Calendar                | [data](docs/sources/api-response-examples/g-workspace/calendar) - [rules](docs/sources/example-rules/google-workspace/calendar.yaml)    | `calendar.readonly`                                                                                                                                                                                                                               |
| Google Chat                    | [data](docs/sources/api-response-examples/g-workspace/google-chat) - [rules](docs/sources/example-rules/google-workspace/google-chat.yaml) | `admin.reports.audit.readonly`                                                                                                                                                                                                                    |
| Google Directory               | [data](docs/sources/api-response-examples/g-workspace/directory) - [rules](docs/sources/example-rules/google-workspace/directory.yaml)  | `admin.directory.user.readonly admin.directory.user.alias.readonly admin.directory.domain.readonly admin.directory.group.readonly admin.directory.group.member.readonly admin.directory.orgunit.readonly admin.directory.rolemanagement.readonly` |
| Google Drive                   | [data](docs/sources/api-response-examples/g-workspace/gdrive-v3) - [rules](docs/sources/example-rules/google-workspace/gdrive-v3.yaml)  | `drive.metadata.readonly`                                                                                                                                                                                                                         |
| GMail                          | [data](docs/sources/api-response-examples/g-workspace/gmail) - [rules](docs/sources/example-rules/google-workspace/gmail.yaml)          | `gmail.metadata`                                                                                                                                                                                                                                  |
| Google Meet                    | [data](docs/sources/api-response-examples/g-workspace/meet) - [rules](docs/sources/example-rules/google-workspace/meet.yaml)            | `admin.reports.audit.readonly`                                                                                                                                                                                                                    |

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
must authorize the Azure Enterprise Application you provision (with [provided terraform modules](infra/examples)) to access your Microsoft 365 tenant's data with the scopes listed
below. This is done via the Azure Portal (Active Directory).  If you use our provided Terraform
modules, specific instructions that you can pass to the Microsoft 365 Admin will be output for you.

| Source&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Examples &nbsp;&nbsp;&nbsp;&nbsp;                                                                                                    | Application Scopes                                                                                 |
|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Active Directory                                                                                 | [data](docs/sources/api-response-examples/microsoft-365/directory) - [rules](docs/sources/example-rules/microsoft-365/directory.yaml)       | `User.Read.All` `Group.Read.All`                                                                   |
| Calendar                                                                                               | [data](docs/sources/api-response-examples/microsoft-365/outlook-cal) - [rules](docs/sources/example-rules/microsoft-365/outlook-cal.yaml)   | `User.Read.All` `Group.Read.All` `OnlineMeetings.Read.All` `Calendars.Read` `MailboxSettings.Read` |
| Mail                                                                                                   | [data](docs/sources/api-response-examples/microsoft-365/outlook-mail) - [rules](docs/sources/example-rules/microsoft-365/outlook-mail.yaml) | `User.Read.All` `Group.Read.All`  `Mail.ReadBasic.All` `MailboxSettings.Read`                      |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs).
Please refer to that module for a definitive list.

See details: [docs/sources/msft-365/readme.md](docs/sources/msft-365/readme.md)

### Other Data Sources via REST APIs

These sources will typically require some kind of "Admin" within the tool to create an API key or
client, grant the client access to your organization's data, and provide you with the API key/secret
which you must provide as a configuration value in your proxy deployment.

The API key/secret will be used to authenticate with the source's REST API and access the data.

| Source     | Details + Examples                                              | API Permissions / Scopes                                                                                       |
|------------|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Asana      | [docs/sources/asana](docs/sources/asana.md)                     | a [Service Account](https://asana.com/guide/help/premium/service-accounts) (provides full access to Workspace) |
| Salesforce | [docs/sources/salesforce](docs/sources/salesforce.md)           | `api` `chatter_api` `refresh_token` `offline_access` `openid` `lightning` `content` `cdp_query_api`            |                                                                                                       |
| Slack      | [docs/sources/slack-discovery](docs/sources/slack-discovery.md) | `discovery:read`                                                                                               |
| Zoom       | [docs/sources/zoom](docs/sources/zoom.md)                       | `user:read:admin` `meeting:read:admin` `report:read:admin`                                                     |

NOTE: the above scopes are copied from [infra/modules/worklytics-connector-specs](infra/modules/worklytics-connector-specs).
Please refer to that module for a definitive list.

### Other Data Sources without REST APIs

Other data sources, such as HRIS, Badge, or Survey data can be exported to a CSV file. The "bulk"
mode of the proxy can be used to pseudonymize these files by copying/uploading the original to
a cloud storage bucket (GCS, S3, etc), which will trigger the proxy to sanitize the file and write
the result to a 2nd storage bucket, which you can grant Worklytics access to read.

Alternatively, the proxy can be used as a command line tool to pseudonymize arbitrary CSV files
(eg, exports from your  HRIS), in a manner consistent with how a psoxy instance will pseudonymize
identifiers in a target REST API. This is REQUIRED if you want SaaS accounts to be linked with HRIS
data for analysis (eg, Worklytics will match email set in HRIS with email set in SaaS tool's account
so these must be pseudonymized using an equivalent algorithm and secret). See [`java/impl/cmd-line/`](/java/impl/cmd-line)
 for details.

## Getting Started - Customers

### Prerequisites
As of Feb 2023, Psoxy is implemented with Java 11 and built via Maven. The proxy infrastructure is
provisioned and the Psoxy code deployed using Terraform, relying on Azure, Google Cloud, and/or AWS
command line tools.

You will need all of the following:

| Tool                                         | Version        | Test Command              |
|----------------------------------------------|----------------|---------------------------|
| [git](https://git-scm.com/)                  | 2.17+          | `git --version`           |
| [Maven](https://maven.apache.org/)           | 3.6+           | `mvn -v`                  |
| [Java 11+ JDK](https://openjdk.org/install/) | 11+, but < 19  | `mvn -v &#124; grep Java` |
| [Terraform](https://www.terraform.io/)       | 1.3.x, but < 1.4.x | `terraform version`       |


NOTE: Java 19 is currently broken, see [docs/troubleshooting.md](docs/troubleshooting.md); we suggest
Java 17, which is a LTS edition.

NOTE: Using `terraform` is not strictly necessary, but it is the only supported method. You may
provision your infrastructure via your host's CLI, web console, or another infrastructure provisioning
tool, but we don't offer documentation or support in doing so.  Adapting one of our
[terraform examples](infra/examples) or writing your own config that re-uses our
[modules](infra/modules) will simplify things greatly.

NOTE: Refrain to use versions 1.4.x as contain a bug that breaks the infrastructure deployment and not yet officially supported. Bug will be solved apparently in v1.4.3+.

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


We provide a script to check these prereqs, at [`tools/check-prereqs.sh`](tools/check-prereqs.sh).
That script has no dependencies itself, so should be able to run on any plain POSIX-compliant shell
(eg,`bash`, `zsh`, etc) that we'd expect you to find on most Linux, MacOS, or even Windows with
Subsystem for Linux (WSL) platforms.

```shell
# from the root of a clone of this repository

./tools/check-prereqs.sh
```


### Setup

  1. Choose the cloud platform you'll deploy to, and follow its guide:
       - [AWS](docs/aws/getting-started.md)
       - [Google Cloud platform](docs/gcp/getting-started.md)

  2. Pick the location from which you will provision the psoxy instance. Some suggestions:

     - [Google Cloud Shell](https://cloud.google.com/shell/) - if you're using GCP or connecting to
       Google Workspace, this is a recommended option. It [includes the prereqs above](https://cloud.google.com/shell/docs/how-cloud-shell-works#tools) EXCEPT aws/azure CLIs.
     - Ubuntu Linux VM/Container - we provide some setup instructions covering [prereq installation](docs/prereqs-ubuntu.md)
       for Ubuntu variants of Linux, and specific authentication help for:
            - [EC2](docs/aws/getting-started.md)

  3. clone the public repo (or, alternatively, [create a private fork](docs/private-fork.md) and
     clone that; we recommend this if you're going to commit stuff you want to share privately with
     other members of your team).
```shell
git clone https://github.com/Worklytics/psoxy.git
```

  4. Pick an example for `infra/examples/` and copy it.
    - Eg `cp -r infra/examples/aws-msft-365 infra/examples/acme-com`.
    - Create a branch in your local clone (eg `git checkout -b acme-com`).
        - NOTE: do not push this branch back to the remote, unless you want your changes to be
          publicly visible. If you want to use `git` to manage your changes outside your local
          machine, create private fork (described above).
    - modify the `.gitignore` file in the directory so that git will manage your terraform state/
      variables, if you wish to commit these things to repo.

  5. create a [terraform](https://www.terraform.io/) configuration, setting up your environment,
     psoxy instances, and API keys/secrets for each connection
     a. various examples are provided in [`infra/examples`](/infra/examples)
     b. various modules are provided in [`infra/modules`](/infra/modules); these modules will either
        perform all the necessary setup, or create TODO files explaining what you must do outside
        Terraform

  6. init Terraform configuration and generate an initial plan
```shell
terraform init
terraform plan -out=tfplan.out
```

  7. review the plan and ensure it matches the infrastructure you expect:
```shell
terraform show tfplan.out
```

Edit your Terraform configuration to modify/remove resources as needed.

Use `terraform import` where needed for Terraform to re-use existing resources, rather than
recreate them (for example, to use GCP project that already exists).

  9. apply your configuration
```shell
terraform apply
```

  10. follow any `TODO` instructions produced by Terraform, such as:
     - build and deploy JAR (built from this repo) into your environment
     - provision API keys / make OAuth grants needed by each Data Connection
     - create the Data Connection from Worklytics to your psoxy instance (Terraform can provide
       `TODO` file with detailed steps for each)

  11. Various test commands are provided in local files, as the output of the Terraform; you may use
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
