# infra for a Google Workspace API connector
#  (eg, OAuth Client in a GCP project)

# TODO: extract this to its own repo or something, so can consume from our main infra repo. it's
# similar to src/modules/google-workspace-dwd-connector/main.tf in the main infra repo

# service account to personify connector
resource "google_service_account" "connector-sa" {
  account_id   = var.connector_service_account_id
  display_name = var.display_name
  description  = var.description
  project      = var.project_id
}

resource "google_project_service" "apis_needed" {
  for_each = toset(var.apis_consumed)

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false
}



# enable domain-wide-delegation via GCP console
# NOTE: side effect of enabling domain-wide-delegation is that an "OAuth 2.0 Client ID" will be
# created for the service account and listed in GCP Console
# TODO: specify the following step in in terraform once https://github.com/hashicorp/terraform-provider-google/issues/1959 solved
resource "local_file" "todo" {
  filename = "TODO - ${var.display_name} setup.md"
  content  = <<EOT
Complete the following steps via GCP console:
  1. Visit https://console.cloud.google.com/apis/credentials?project=${var.project_id}
  2. Find the `${var.connector_service_account_id}` service account.
  3. Enable 'Domain-wide Delegation' and 'Save'. This provisions an 'Oauth 2.0 client' for the
     service account. Copy the Client ID of that client.

Complete the following steps via the Google Workspace Admin console:
   1. Visit https://admin.google.com/ and navigate to Security --> API Controls, then find "Manage
      Domain Wide Delegation". Click "Add new"
   2. Copy and paste the client ID from the prior section into the "Client ID" input in the popup.
   3. Copy and paste the scopes for your data source (see `java/configs/`, and find the `SCOPE`
      variable in the yaml file that matches your source) into the "Scopes" input.
   4. Authorize it.

With this, your psoxy instance should be able to authenticate with Google as `${var.connector_service_account_id}`
and request data from Google as authorized by the OAuth scopes you granted.
EOT

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
