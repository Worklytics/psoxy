locals {
  secret_id = "PSOXY_ACCESS_TOKEN_${var.function_name}"
}

# secret to hold a long-lived access token
# previously created by module gcp-oauth-long-access-strategy
data "google_secret_manager_secret" "access_token" {
  project   = var.project_id
  secret_id = local.secret_id
}
