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

## Data Flow

A Psoxy instances reside on your premises (in the cloud) and act as an intermediary between
Worklytics and the data source you wish to connect.  In this role, the proxy performs the
authentication necessary to connect to the data source's API and then any required transformation
(such as pseudonymization or redaction) on the response.

Orchestration continues to be performed on the Worklytics-side.

![proxy illustration](docs/proxy-illustration.png)

## Getting Started - Customers

### Prereqs
As of Oct 2021, Psoxy is implemented with Java 11 and built via Maven. Infrastructure is provisioned
via Terraform, relying on Google Cloud command line tools.  You will need recent versions of all of
the following:

- Java 11+ JDK variant
- [Maven 3.6+](https://maven.apache.org/docs/history.html)
- [Google Cloud Command Line tool](https://cloud.google.com/sdk/docs/install), configured for the
  GCP project that will host your psoxy instance(s)
- [terraform](https://www.terraform.io/) optional; if you don't use this, you'll need to configure
  your GCP project via the console/gcloud tool. Writing your own terraform config that re-uses
  our modules will simplify things greatly.

([Google Cloud Shell](https://cloud.google.com/shell/docs/how-cloud-shell-works#tools) provides all
of the above)

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
   a. various examples are provided in [`infra`](/infra/)
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
        * eDiscovery API *alpha*

You can also use the command line tool to pseudonymize arbitrary CSV files (eg, exports from your
HRIS), in a manner consistent with how a psoxy instance will pseudonymize identifiers in a target
REST API. This is REQUIRED if you want SaaS accounts to be linked with HRIS data for analysis (eg,
Worklytics will match email set in HRIS with email set in SaaS tool's account - so these must be
pseudonymized using an equivalent algorithm and secret). See [`java/impl/cmd-line/`](/java/impl/cmd-line)
for details.

## Getting Started - Developers

The prereqs as above apply (java, maven, etc).

With those, you can can run locally via IntelliJ, using run configs (located in `.idea/runConfigurations`):
  - `package install core` builds the core JAR, on which implementations depend
  - `gcp - run gmail` builds and runs a local instance for GMail

Or from command line:
```shell
cd java/impl/gcp
mvn function:run -Drun.functionTarget=co.worklytics.psoxy.Route
```

By default, that serves the function from http://localhost:8080.

### GMail Example

1.) run `terraform init` and `terraform apply` from `infra/dev-personal` to provision environment

#### Local
2.) run locally via IntelliJ run config

3.) execute the following to verify your proxy is working OK

Health check (verifies that your client can reach and invoke the proxy at all; and that is has sensible config)
```shell
curl -iX GET \
http://localhost:8080/ \
-H "X-Psoxy-Health-Check: true"
```

```shell
export PSOXY_USER_TO_IMPERSONATE={{--identifier of GCP user to impersonate (eg, mailbox owner, Google ID or email)--}}
```

```shell
curl -X GET \
http://localhost:8080/gmail/v1/users/me/messages \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)"
```

Using a message id you grab from that:
```shell
curl -X GET \
http://localhost:8080/gmail/v1/users/me/messages/1743b19234726ef3f\?format=metadata \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)"
```

#### Cloud

1.) deploy to GCP using Terraform (see `infra/`). Follow steps in any TODO files it generates.

2.) Set your env vars: (these should be in a TODO file generated by terraform in prev step
```shell
export PSOXY_GCP_PROJECT={{--GCP project id that hosts your instance--}}
export PSOXY_GCP_REGION=us-central1 # change this to whatever the default is for you project
export PSOXY_HOST=`echo $PSOXY_GCP_REGION"-"$PSOXY_GCP_PROJECT`
```

3.) grant yourself access (probably not needed if you have primitive role in project, like Owner or
Editor)
```shell
gcloud alpha functions add-iam-policy-binding psoxy-gmail --region=$PSOXY_GCP_REGION --member=user:$(gcloud config get-value core/account) --role=roles/cloudfunctions.invoker --project=$PSOXY_PROJECT_ID
```

alternatively, you can add Terraform resource for this to your Terraform config, and apply it again:
```shell
resource "google_cloudfunctions_function_iam_member" "member" {
  project        = "YOUR_GCP_PROJECT_ID_HERE"
  region         = "YOUR_GCP_REGION_OF_YOUR_CLOUD_FUNCTION_HERE" # eg, us-central1
  cloud_function = "psoxy-gmail" #TODO: change if you're doing something OTHER than gmail
  role           = "roles/cloudfunctions.invoker"
  member         = "user:YOUR_EMAIL_HERE"
}
```

Either way, if this function is for prod use, please remove these grants after you're finished
testing.

4.) invocation examples

```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gmail/gmail/v1/users/me/messages \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)"
```

```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-gmail/gmail/v1/users/me/messages/17c3b1911726ef3f\?format=metadata \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)"
```

```shell
curl -X GET \
https://`echo $PSOXY_HOST`.cloudfunctions.net/psoxy-google-chat/admin/reports/v1/activity/users/all/applications/chat \
-H "Authorization: Bearer $(gcloud auth print-identity-token)" \
-H "X-Psoxy-User-To-Impersonate: $(echo $PSOXY_USER_TO_IMPERSONATE)"
```
