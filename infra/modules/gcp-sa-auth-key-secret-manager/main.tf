# DEPRECATED - use composition of gcp-sa-auth-key + gcp-secrets
# infra to support authentication of a service as a Google Workspace connector using a service
# account key


# this is only one approach to authentication; others may be more appropriate to your use-case.

# TODO: extract this to its own repo or something, so can consume from our main infra repo. it's
# similar to src/modules/google-workspace-dwd-connector/main.tf in the main infra repo



# note this requires the terraform to be run regularly
resource "time_rotating" "sa-key-rotation" {
  rotation_days = var.rotation_days
}

resource "google_service_account_key" "key" {
  service_account_id = var.service_account_id

  # does this actually destroy/disable the old key? that's a problem as Cloud functions pull the
  # value at instance start-up and don't refresh it
  keepers = {
    rotation_time = time_rotating.sa-key-rotation.rotation_rfc3339
  }

  lifecycle {
    create_before_destroy = true
  }
}

module "gcp-secrets" {
  source = "../gcp-secrets"

  secret_project  = var.secret_project
  replica_regions = var.replica_regions

  secrets = {
    "${var.secret_id}" = google_service_account_key.key.private_key
  }
}

moved {
  from = google_secret_manager_secret.service-account-key
  to   = module.gcp-secrets.google_secret_manager_secret[var.secret_id]
}

output "key_secret_id" {
  value = module.gcp-secrets.secret_ids[var.secret_id]
}

output "key_secret_version_number" {
  value = module.gcp-secrets.secret_version_numbers[var.secret_id]
}

output "key_value" {
  value = google_service_account_key.key.private_key
}
