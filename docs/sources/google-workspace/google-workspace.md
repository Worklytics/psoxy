# Google Workspace

Google Workspace sources can be setup via Terraform, using modules found in our GitHub repo.  These
are included in the examples found in `[infra/examples/](../../infra/examples).

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
permissions:
  * Admin API
  * Domain Settings
  * Groups
  * Organizational Units
  * Reports (required only if you are connecting to the Audit Logs, used for Google Chat, Meet, etc)
  * Users
    You may use a predefined role, or define a [Custom Role](https://support.google.com/a/answer/2406043?fl=1).

The email address of the account you created will be used when creating the data connection to the
Google Directory in the Worklytics portal. Provide it as the value of the 'Google
Account to Use for Connection' setting when they create the connection.



