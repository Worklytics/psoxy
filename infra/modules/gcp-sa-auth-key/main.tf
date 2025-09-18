# infra to support authentication of a service as a Google Workspace connector using a service
# account key
# this is only one approach to authentication; others may be more appropriate to your use-case.
# note this requires the terraform to be run regularly
# q: even a worthwhile module? it's just a key with rotation ...


module "tf_runner" {
  source = "../../modules/gcp-tf-runner"
}

# grant this directly on SA, jit for when we know it is needed to create keys
# (SA keys are needed only for SAs that are used to connect to Google Workspace APIs)
resource "google_service_account_iam_member" "key_admin" {
  member             = module.tf_runner.iam_principal
  role               = "roles/iam.serviceAccountKeyAdmin"
  service_account_id = var.service_account_id
}

resource "time_rotating" "sa-key-rotation" {
  rotation_days = var.rotation_days
}

resource "google_service_account_key" "key" {
  service_account_id = var.service_account_id

  lifecycle {
    create_before_destroy = true
    replace_triggered_by = [
      time_rotating.sa-key-rotation
    ]
  }

  depends_on = [
    google_service_account_iam_member.key_admin
  ]
}
