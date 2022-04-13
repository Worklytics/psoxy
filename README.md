# psoxy
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

As of Dec 2021, psoxy instances may be hosted in [Google Cloud ](docs/gcp/development.md) or
[AWS](docs/aws/getting-started.md).

## Data Flow

A Psoxy instances reside on your premises (in the cloud) and act as an intermediary between
Worklytics and the data source you wish to connect.  In this role, the proxy performs the
authentication necessary to connect to the data source's API and then any required transformation
(such as pseudonymization or redaction) on the response.

Orchestration continues to be performed on the Worklytics-side.

![proxy illustration](docs/proxy-illustration.jpg)

## Getting Started - Customers

### Prereqs
As of Oct 2021, Psoxy is implemented with Java 11 and built via Maven. Infrastructure is provisioned
via Terraform, relying on Google Cloud and/or AWS command line tools .  You will need recent
versions of all of the following:

  - git
  - Java 11+ JDK variant
  - [Maven 3.6+](https://maven.apache.org/docs/history.html)
  - [terraform](https://www.terraform.io/) optional; if you don't use this, you'll need to configure
    your GCP/AWS project via the web console/CLI tools. Writing your own terraform config that
    re-uses our modules will simplify things greatly.

And, depending on your scenario, you may also need:
  - [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) is
    required to deploy your psoxy instances in AWS.
  - [Google Cloud Command Line tool](https://cloud.google.com/sdk/docs/install) Required to host
    your psoxy instances in GCP *OR* if you plan to connect Google Workspace as a data source. It
    should be configured for the GCP project that will host your psoxy instance(s) and/or your
    connectors.
  - [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli) Required to connect to
    Microsoft 365 sources.
  - [openssl](https://www.openssl.org/) If generating local certificates (see
    [`infra/modules/azure-local-cert`](infra/modules/azuread-local-cert))

If you don't require Azure CLI / AWS CLI, [Google Cloud Shell](https://cloud.google.com/shell/docs/how-cloud-shell-works#tools)
provides all of the above out-of-the-box, simplifies authentication/authorization for GCP
deployments, and provides security advantages.

### Setup
1. contact support@worklytics.co to ensure your Worklytics account is enabled for Psoxy, and to get
   the email of your Worklytics tenant's service account.
2. OPTIONAL; create a private fork of this repo; we recommend this to allow you to commit your
   specific configurations/changes while continuing to periodically fetch any changes from public
   repo. See [Duplicating a Repo](https://docs.github.com/en/repositories/creating-and-managing-repositories/duplicating-a-repository),
   for guidance. Specific commands for Psoxy repo are below
```shell
# set up the mirror
git clone --bare https://github.com/Worklytics/psoxy.git
cd psoxy
git push --mirror https://github.com/{{YOUR_GITHUB_ORG_ID}}/psoxy-private.git
cd ..
rm -rf psoxy
git clone https://github.com/{{YOUR_GITHUB_ORG_ID}}/psoxy-private.git

# set the public repo as 'upstream' remote
git remote add upstream git@github.com:worklytics/psoxy.git
git remote set-url --push upstream DISABLE

# fetch, rebase on top of your work
git fetch upstream
git rebase upstream/main
```

3. create a [terraform](https://www.terraform.io/) configuration, setting up your environment, psoxy
   instances, and API keys/secrets for each connection
   a. various examples are provided in [`infra/examples`](/infra/examples)
   b. various modules are provided in [`infra/modules`](/infra/modules); these modules will either
      perform all the necessary setup, or create TODO files explaining what you must do outside
      Terraform
4. init and apply the Terraform configuration:
```shell
terraform init
terraform apply
```
5. follow any `TODO` instructions produced by Terraform, such as:
  - build and deploy JAR (built from this repo) into your environment
  - provision API keys / make OAuth grants needed by each Data Connection
  - create the Data Connection from Worklytics to your psoxy instance (Terraform can provide `TODO`
    file with detailed steps for each

## Supported Data Sources
Data source connectors will be marked with their stage of maturity:
  * *alpha* - preview, YMMV, still active development; only available to select pilot customers.
  * *beta* - available to all customers, but still under active development and we expect bugs in some
           environments.
  * *general availability* - excepted to be stable and reliable.

As of Sept 2021, the following sources can be connected via psoxy:
    * Google Workspace
      * Calendar *beta*
      * Chat *beta*
      * Directory *beta*
      * Drive *beta*
      * GMail *beta*
      * Meet *beta*
    * Slack
        * eDiscovery API *beta*

You can also use the command line tool to pseudonymize arbitrary CSV files (eg, exports from your
HRIS), in a manner consistent with how a psoxy instance will pseudonymize identifiers in a target
REST API. This is REQUIRED if you want SaaS accounts to be linked with HRIS data for analysis (eg,
Worklytics will match email set in HRIS with email set in SaaS tool's account - so these must be
pseudonymized using an equivalent algorithm and secret). See [`java/impl/cmd-line/`](/java/impl/cmd-line)
for details.


## Support

Psoxy is maintained by Worklytics, Co. Support as well as professional services to assist with configuration and customization are available. Please contact [sales@worklytics.co](mailto:sales@worklytics.co) for more information or visit [www.worklytics.co](https://www.worklytics.co).
