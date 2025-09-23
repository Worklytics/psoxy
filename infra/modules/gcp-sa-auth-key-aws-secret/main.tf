# infra to support authentication of a service as a Google Workspace connector using a service
# account key

# this is only one approach to authentication; others may be more appropriate to your use-case.

module "tf_runner" {
  source = "../../modules/gcp-tf-runner"
  
  tf_gcp_principal_email = var.tf_gcp_principal_email
}

# grant this directly on SA, jit for when we know it is needed to create keys
# (SA keys are needed only for SAs that are used to connect to Google Workspace APIs)
resource "google_service_account_iam_member" "key_admin" {
  member             = module.tf_runner.iam_principal
  role               = "roles/iam.serviceAccountKeyAdmin"
  service_account_id = var.service_account_id
}

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

  depends_on = [
    google_service_account_iam_member.key_admin,
  ]
}

resource "aws_ssm_parameter" "value" {
  name        = var.secret_id
  type        = "SecureString"
  description = "Key for gcp service account ${var.service_account_id}"
  value       = google_service_account_key.key.private_key
  key_id      = coalesce(var.kms_key_id, "alias/aws/ssm")

  lifecycle {
    ignore_changes = [
      tags,
    ]
  }
}
