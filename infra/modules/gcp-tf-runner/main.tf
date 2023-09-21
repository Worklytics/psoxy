# logic to get (and determine) useful information about the GCP entity that Terraform is auth'd as
#
# NOTE: this is not *proper* Terraform style, as re-use of it will result in "deeper" Terraform
# module structure, where modules call all modules in very hierarchical way. But it was repeated 4-5
# times throughout the code base, and includes some hard-coded convention stuff that imho is better
# to have in one place.


# in cloud shell, this seems to return {"email":"", "id":""}
data "google_client_openid_userinfo" "me" {

}

data "external" "gcloud_active_account" {
  count = data.google_client_openid_userinfo.me.email == "" ? 1 : 0

  # NOTE: in Google Cloud Shell, if impersonating a service account when applying terraform, then
  # I think this will be wrong ... will be the authenticated principal, not the SA being impersonated
  program = ["gcloud", "auth", "list", "--filter=status:ACTIVE", "--format=\"value(account)\""]
}

# IDEA: create id_token and parse it???
# but resulting token is sensitive, so will this work
# data "google_service_account_id_token" "oidc" {
# target_audience = "https://your.cloud.run.app/"
# }
# data.google_service_account_id_token.oidc.id_token is JWT - so would need to parse it, pull 'email' claim

locals {
  # coalesce failing here implies we failed to detect the auth'd gcp user
  authed_user_email = coalesce(
    data.google_client_openid_userinfo.me.email,
    try(data.external.gcloud_active_account[0].result, "")
  )

  # hacky way to determine if Terraform running as a service account or not
  tf_is_service_account = endswith(data.google_client_openid_userinfo.me.email, "iam.gserviceaccount.com")

  tf_qualifier = local.tf_is_service_account ? "serviceAccount:" : "user:"
  tf_principal = "${local.tf_qualifier}${data.google_client_openid_userinfo.me.email}"
}

output "email" {
  value       = data.google_client_openid_userinfo.me.email
  description = "The email address of the Terraform runner."
}

output "is_service_account" {
  value       = local.tf_is_service_account
  description = "Whether Terraform is running as a service account or not."
}

output "iam_principal" {
  value       = local.tf_principal
  description = "The Terraform runner as a 'principal' for use in GCP IAM policies."
}
