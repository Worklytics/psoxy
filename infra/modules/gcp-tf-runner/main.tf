# logic to get (and determine) useful information about the GCP entity that Terraform is auth'd as
#
# NOTE: this is not *proper* Terraform style, as re-use of it will result in "deeper" Terraform
# module structure, where modules call all modules in very hierarchical way. But it was repeated 4-5
# times throughout the code base, and includes some hard-coded convention stuff that imho is better
# to have in one place.


# in cloud shell, this seems to return {"email":"", "id":""}
# in any env, this is NEVER the gcp service account configured via provider block
# (eg, google.impersonate_service_account = "terraform@...")
data "google_client_openid_userinfo" "me" {
}

# generate id token for the current user, from which we could parse email of that user
# such parsing is explicitly allowed by Google; see https://cloud.google.com/docs/authentication/token-types#id
#
# however, this still appears to be the email of the authenticated user, not service account that
# it's impersonating via the provider configuration
#
# we could pass in the service account email as a variable; but that pollutes a LOT of interfaces
# and calls into question why we even use this module at all
data "google_service_account_id_token" "identity" {
  target_audience = "worklytics.co/gcp-tf-runner"
}

# TOD: external is hacky, likely not to work in many contexts; can we conditionally avoid it?
# - does it work in terraform cloud? likely not ...
# - should work locally, and in cloud shell
# - would guess some tf linters/etc would complain about this
data "external" "identity" {
  program = ["./${path.module}/read-tfvars.sh", "gcp_terraform_sa_account_email"]
}

# alternative ideas
#  - data.google_client_config has an 'access_token', but that's opaque so can't parse email from it
#  - call outs to gcloud CLI via data.external --> still won't know about impersonation
#  - is there some resource that has an attribute that's the service account used to create it?

locals {
  # Only use the JWT token if we don't have an email from google_client_openid_userinfo, attempt to parse one from the JWT payload of the ID token
  jwt_payload = data.google_client_openid_userinfo.me.email == "" ? try(split(".", data.google_service_account_id_token.identity.id_token)[1], "") : ""

  # convert base64url encoding to base64 encoding
  padding                   = join("", formatlist("%s", [for _ in range(4 - length(local.jwt_payload) % 4) : "="]))
  jwt_payload_padded        = "${local.jwt_payload}${local.padding}"
  jwt_payload_base64encoded = replace(replace(local.jwt_payload_padded, "-", "+"), "_", "/")

  # decode to JSON, then extract email field - only if we have a JWT payload
  email_from_jwt = local.jwt_payload != "" ? try(nonsensitive(jsondecode(base64decode(local.jwt_payload_base64encoded)).email), "") : ""

  # coalesce failing here implies we failed to detect the auth'd gcp user
  authed_user_email = coalesce(
    try(data.external.identity.result.gcp_terraform_sa_account_email, ""), # "" if no such value
    data.google_client_openid_userinfo.me.email,
    local.email_from_jwt
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
