# infra to support authentication of a service as a Google Workspace connector using a service
# account key

# this is only one approach to authentication; others may be more appropriate to your use-case.

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
}

resource "aws_secretsmanager_secret" "service-account-key" {
  name = var.secret_id
}

resource "aws_secretsmanager_secret_version" "service-account-key-version" {
  secret_id     = aws_secretsmanager_secret.service-account-key.id
  secret_string = google_service_account_key.key.private_key
}
