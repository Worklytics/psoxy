# Google Workspace

Google Workspace sources can be setup via Terraform, using modules found in our GitHub repo.  These
are included in the examples found in `[infra/examples/](../../infra/examples).

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


## Without Terraform

Instructions for how to setup Google Workspace without terraform:

  1. Create or choose the GCP project in which to create the OAuth Clients.
  2. Activate relevant API(s) in the project.
  3. Create a Service Account and a JSON key for the service account.
  4. Base64-encode the key and store it as a Systems Manager Parameter in AWS (same region as your
     lambda function deployed).  The parameter name should be something like PSOXY_GDIRECTORY_SERVICE_ACCOUNT_KEY.
  5. Get the numeric ID of the service account. Use this plus the oauth scopes to make domain-wide
     delegation grants via the Google Workspace admin console.

NOTE: you could also use a single Service Account for everything, but you will need to store it
repeatedly in AWS for each parameter name.




