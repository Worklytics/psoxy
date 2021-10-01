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
# NOTE: after apply, enable domain-wide-delegation in GCP console on the SA
# goto: https://console.cloud.google.com/apis/credentials?organizationId=496339493825&project=eval-engin
# NOTE: done in prod 27 Aug 2019 by erik
# TODO: specify in terraform once https://github.com/hashicorp/terraform-provider-google/issues/1959 solved
# NOTE: side effect of enabling domain-wide-delegation is that an "OAuth 2.0 Client ID" will be
# created for the service account and listed in GCP Console


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
