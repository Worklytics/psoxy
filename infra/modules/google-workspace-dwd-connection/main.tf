# infra for a Google Workspace API connector
#  (eg, OAuth Client in a GCP project)

# TODO: extract this to its own repo or something, so can consume from our main infra repo. it's
# similar to src/modules/google-workspace-dwd-connector/main.tf in the main infra repo

locals {
  # sa_account_ids must be 6-30 chars, and must start with a letter, use only lowercase letters,
  # numbers and - (inside)
  # see https://registry.terraform.io/providers/hashicorp/google/latest/docs/resources/google_service_account

  trimmed_id = trim(var.connector_service_account_id, " ")

  # TODO: md5 here is 32 chars of hex, so some risk of collision by truncating, while could use
  sa_account_id = length(local.trimmed_id) < 31 ? lower(replace(local.trimmed_id, " ", "-")) : substr(md5(local.trimmed_id), 0, 30)
}

# service account to personify connector
resource "google_service_account" "connector-sa" {
  project      = var.project_id
  account_id   = var.connector_service_account_id
  display_name = var.display_name
  description  = var.description
}

resource "google_project_service" "apis_needed" {
  for_each = toset(var.apis_consumed)

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false
}

locals {
  # alternatively, could test if prefix of ANY needed scope starts with 'https://www.googleapis.com/auth/admin',
  # but in a sense that's a little hackier as is exploiting implementation details of Google's OAuth scope format

  # additionally, each of these OAuth scopes would seem tp imply *specific* permissions for the role
  # that the service account needs; so we could formally define the subset
  scopes_requiring_admin_account = [
    "https://www.googleapis.com/auth/admin.directory.user.readonly",
    "https://www.googleapis.com/auth/admin.directory.user.alias.readonly",
    "https://www.googleapis.com/auth/admin.directory.domain.readonly",
    "https://www.googleapis.com/auth/admin.directory.group.readonly",
    "https://www.googleapis.com/auth/admin.directory.group.member.readonly",
    "https://www.googleapis.com/auth/admin.directory.orgunit.readonly",
    "https://www.googleapis.com/auth/admin.directory.rolemanagement.readonly",
    "https://www.googleapis.com/auth/admin.reports.audit.readonly",
  ]
  google_workspace_admin_account_required = (length(setintersection(local.scopes_requiring_admin_account, var.oauth_scopes_needed)) > 0)
  google_workspace_service_account_setup  = <<EOT
  5. Create an account to act as a 'Service Account' for the connection in your Google Workspace
     Directory. This is not to be confused with a GCP Service Account. Rather, this is a regular
     Google Workspace user account, but intended to be assigned to a service rather than a human
     user. Your proxy instance will impersonate this user when accessing the [Google Admin Directory](https://developers.google.com/admin-sdk/directory/v1/guides)
     and [Reports](https://developers.google.com/admin-sdk/reports/v1/guides) APIs. (Google requires
     that these be accessed via impersonation of a Google user account, rather than directly using
     a GCP service account).

     We recommend naming the account `svc-worklytics@{your-domain.com}`.

     If you have already created a sufficiently privileged service account user for a different
     Google Workspace connection, you can re-use that one.

  6. Assign the account a sufficiently privileged role. At minimum, the role must have permission
     to READ the following [Administrator Setting Privileges](https://support.google.com/a/answer/1219251):
       * Admin API
       * Domain Settings
       * Groups
       * Organizational Units
       * Reports (required only if you are connecting to the Audit Logs, used for Google Chat, Meet, etc)
       * Users
     You may use a predefined role, or define a [Custom Role](https://support.google.com/a/answer/2406043?fl=1).

(NOTE: Steps 5/6 are optional, but highly recommended. You could use the account of a sufficiently
privileged human user, but then should you ever remove that user or revoke privileges, your
connection to will fail)

  7. Send the email address of the account you created to the administrator who will create the
     connection in the Worklytics portal. They will need to provide it as the value of the 'Google
     Account to Use for Connection' setting when they create the connection.

  8. Optionally, you may also set the email address of the account you created the value of
     `google_workspace_example_user` in your `terraform.tfvars` file. This will cause the example
     API invocations generated by the terraform modules to prefill this value as the account to
     impersonate on those requests. This will allow you to validate the permissions of the account,
     as well as the ability of the proxy connection to impersonate it.
EOT

  todo_content = <<EOT
Complete the following steps via the Google Workspace Admin console:
   1. Visit https://admin.google.com/ and navigate to "Security" --> "Access and Data Control" -->
      "API Controls", then find "Manage Domain Wide Delegation". Click "Add new".

   2. Copy and paste client ID `${google_service_account.connector-sa.unique_id}` into the
      "Client ID" input in the popup. (this is the unique ID of the GCP service account with
       email `${google_service_account.connector-sa.email}`; you can verify it via the GCP console,
       under "IAM & Admin" --> "Service Accounts")

   3. Copy and paste the following OAuth 2.0 scope string into the "Scopes" input:
```
${join(",", var.oauth_scopes_needed)}
```

   4. Authorize it. With this, your psoxy instance should be able to authenticate with Google as
      the GCP Service Account `${google_service_account.connector-sa.email}` and request data from
      Google as authorized by the OAuth scopes you granted.
${local.google_workspace_admin_account_required ? local.google_workspace_service_account_setup : ""}
EOT
}

# enable domain-wide-delegation via Google Workspace Admin console
resource "local_file" "todo-google-workspace-admin-console" {
  filename = "TODO ${var.todo_step} - setup ${var.display_name}.md"
  content  = local.todo_content
}


# NOTE: there are several options for how to authenticate a service as the OAuth client created
# above:
#   1.) with a Service Account Key (modules/gcp-sa-auth-key)
#   2.) via a delegation chain (modules/gcp-sa-auth-chain)
#   3.) directly by having process (VM, cloud function, etc) launched "as" the Service Account
#           (probably requires granting some sort of permissions to compute/cloud function SA to
#            be able to launch process as another SA, although by default cloud functions run as
#            app engine SA)
#
# of those (1) is most flexible as works anywhere, but requires a SA key to be managed and kept
# secure, etc; (2) is very clean and has potential via identity payload stuff to work outside GCP,
# but does not work to access Google APIs via impersonation of an end user (which is most of the
# Google Workspace ones, including GMail, GCalendar, etc)
# (3) is limited to GCP environments.
