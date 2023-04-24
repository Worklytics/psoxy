# logic to get (and determine) useful information about the GCP entity that Terraform is auth'd as
#
# NOTE: this is not *proper* Terraform style, as re-use of it will result in "deeper" Terraform
# module structure, where modules call all modules in very hierarchical way. But it was repeated 4-5
# times throughout the code base, and includes some hard-coded convention stuff that imho is better
# to have in one place.

data "google_client_openid_userinfo" "me" {

}

locals {
  # hacky way to determine if Terraform running as a service account or not
  tf_is_service_account = endswith(data.google_client_openid_userinfo.me.email, "iam.gserviceaccount.com")

  tf_qualifier = local.tf_is_service_account ? "serviceAccount:" : "user:"
  tf_principal = "${local.tf_qualifier}${data.google_client_openid_userinfo.me.email}"
}

output "email" {
  value       = data.google_client_openid_userinfo.me.email
  description = "The email address of the Terraform runner"
}

output "is_service_account" {
  value       = local.tf_is_service_account
  description = "Whether Terraform is running as a service account or not"
}

output "iam_principal" {
  value       = local.tf_principal
  description = "The Terraform runner as a 'principal' for use in GCP IAM policies"
}
