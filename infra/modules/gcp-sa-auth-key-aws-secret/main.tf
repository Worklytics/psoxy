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

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ssm_parameter" "value" {
  name        = var.secret_id
  type        = "SecureString"
  description = "Key for gcp service account ${var.service_account_id}"
  value       = google_service_account_key.key.private_key

  lifecycle {
    ignore_changes = [
      tags,
    ]
  }
}
