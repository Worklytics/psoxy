# Google Workspace

Google Workspace sources can be setup via Terraform, using modules found in our GitHub repo.

As of August 2023, we suggest you use one of our template repo, eg:
  - [`aws`](https://github.com/Worklytics/psoxy-example-aws)
  - [`gcp`](https://github.com/Worklytics/psoxy-example-gcp)

Within those, the `google-workspace.tf` and `google-workspace-variables.tf` files in those repos
specify the terraform configuration to use Google Workspace sources.

## Required Permissions

You (the user running Terraform) must have the following roles (or some of the permissions within
them) in the GCP project in which you will provision the OAuth clients that will be used to connect to your Google Workspace
data:

| Role                                                                                                       | Reason |
|------------------------------------------------------------------------------------------------------------| ------ |
| [Service Account Creator](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountCreator) | create Service Accounts to be used as API clients |
| [Service Account Key Admin](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountKeyAdmin) | to access Google Workspace API, proxy *must* be authenticated by a key that you need to create |
| [Service Usage Admin](https://cloud.google.com/iam/docs/understanding-roles#serviceusage.serviceUsageAdmin) | you will need to enable the Google Workspace APIs in your GCP Project |

As these are very permissive roles, we recommend that you use a *dedicated* GCP project so that
these roles are scoped just to the Service Accounts used for this deployment. If you used a shared
GCP project, these roles would give you access to create keys for ALL the service accounts in the
project, for example - which is not good practice.

Additionally, a Google Workspace Admin will need to make a Domain-wide Delegation grant to the
Oauth Clients you create. This is done via the Google Workspace Admin console.  In default setup, this
requires [Super Admin](https://support.google.com/a/answer/2405986?hl=en&fl=1) role, but your
organization may have a Custom Role with sufficient privileges.


## Google Workspace User for Connection

We also recommend you create a dedicated Google Workspace user for Psoxy to use when connecting to
your Google Workspace Admin API, with the specific permissions needed. This avoids the connection
being dependent on a given human user's permissions and improves transparency.

This is not to be confused with a GCP Service Account. Rather, this is a regular
Google Workspace user account, but intended to be assigned to a service rather than a human
user. Your proxy instance will impersonate this user when accessing the [Google Admin Directory](https://developers.google.com/admin-sdk/directory/v1/guides)
and [Reports](https://developers.google.com/admin-sdk/reports/v1/guides) APIs. (Google requires
that these be accessed via impersonation of a Google user account, rather than directly using
a GCP service account).

We recommend naming the account `svc-worklytics@{your-domain.com}`.

If you have already created a sufficiently privileged service account user for a different Google
Workspace connection, you can re-use that one.

Assign the account a sufficiently privileged role. At minimum, the role must have the following
privileges:
  * Admin API
  * Domain Settings
  * Groups
  * Organizational Units
  * Reports (required only if you are connecting to the Audit Logs, used for Google Chat, Meet, etc)
  * Users

Those refer to [Google's documentation](https://support.google.com/a/answer/1219251?fl=1&sjid=8026519161455224599-NA),
as shown below (as of Aug 2023); you can refer there for more details about these privileges.

![google-workspace-admin-privileges.png](google-workspace-admin-privileges.png)

NOTE:
  - you may use a predefined role, or define a [Custom Role](https://support.google.com/a/answer/2406043?fl=1).
  - the proxy rules support restricting access by HTTP method; the Admin SDK API is REST-based, so
    limiting access to `GET` is sufficient to enforce read-only access.

The email address of the account you created will be used when creating the data connection to the
Google Directory in the Worklytics portal. Provide it as the value of the 'Google Account to Use
for Connection' setting when they create the connection.




## General Authentication Overview

Google Workspace APIs use OAuth 2.0 for authentication and authorization. You create an Oauth 2.0
client in Google Cloud Platform and a credential (service account key), which you store in as a
secret in your Proxy instance.

When the proxy connects to Google, it first authenticates with Google API using this secret (a
service account key) by signing a request for a short-lived access token. Google returns this access
token, which the proxy then uses for subsequent requests to Google's APIS until the token expires.

The service account key can be rotated at any time, and the terraform configuration examples we
provide can be configured to do this for you if applied regularly.

More information:
https://developers.google.com/workspace/guides/auth-overview


## Provisioning API clients without Terraform

While not recommend, it is possibly to set up Google API clients without Terraform, via the GCP web
console:

1. Create or choose the GCP project in which to create the OAuth Clients.
2. Activate relevant API(s) in the project.
3. Create a Service Account and a JSON key for the service account.
4. Base64-encode the key and store it as a Systems Manager Parameter in AWS (same region as your
   lambda function deployed).  The parameter name should be something like `PSOXY_GDIRECTORY_SERVICE_ACCOUNT_KEY`.
5. Get the numeric ID of the service account. Use this plus the oauth scopes to make domain-wide
   delegation grants via the Google Workspace admin console.

NOTE: you could also use a single Service Account for everything, but you will need to store it's
key repeatedly in AWS/GCP as the `SERVICE_ACCOUNT_KEY` for each of your Google Workspace connections.
