# logic to get (and determine) useful information about the GCP entity that Terraform is auth'd as
#
# NOTE: this is not *proper* Terraform style, as re-use of it will result in "deeper" Terraform
# module structure, where modules call all modules in very hierarchical way. But it was repeated 4-5
# times throughout the code base, and includes some hard-coded convention stuff that imho is better
# to have in one place.

locals {
  tf_runner_passed_in = var.tf_gcp_principal_email != null && try(length(var.tf_gcp_principal_email) > 0, false)
}



# in cloud shell, this seems to return {"email":"", "id":""}
# in any env, this is NEVER the gcp service account configured via provider block
# (eg, google.impersonate_service_account = "terraform@...")
data "google_client_openid_userinfo" "me" {
  count = local.tf_runner_passed_in ? 0 : 1
}

# alternative ideas
#  - data.google_client_config has an 'access_token', but that's opaque so can't parse email from it
#  - call outs to gcloud CLI via data.external --> still won't know about impersonation
#  - is there some resource that has an attribute that's the service account used to create it?

locals {
  # coalesce failing here implies we failed to detect the auth'd gcp user
  authed_user_email = coalesce(
    var.tf_gcp_principal_email,
    try(data.google_client_openid_userinfo.me[0].email, "")
  )

  # hacky way to determine if Terraform running as a service account or not
  tf_is_service_account = endswith(local.authed_user_email, "iam.gserviceaccount.com")

  tf_qualifier = local.tf_is_service_account ? "serviceAccount:" : "user:"
  tf_principal = "${local.tf_qualifier}${local.authed_user_email}"
}

output "email" {
  value       = local.authed_user_email
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
