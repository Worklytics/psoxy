# infra to support authentication of a service as a Google Workspace connector using a service
# account key
# this is only one approach to authentication; others may be more appropriate to your use-case.
# note this requires the terraform to be run regularly
# q: even a worthwhile module? it's just a key with rotation ...

data "google_client_openid_userinfo" "me" {

}

locals {
  # hacky way to determine if Terraform running as a service account or not
  tf_is_service_account = endswith(data.google_client_openid_userinfo.me.email, "iam.gserviceaccount.com")

  tf_qualifier          = local.tf_is_service_account ? "serviceAccount:" : "user:"
  tf_principal          = "${local.tf_qualifier}${data.google_client_openid_userinfo.me.email}"
}

# grant this directly on SA, jit for when we know it is needed to create keys
# (SA keys are needed only for SAs that are used to connect to Google Workspace APIs)
resource "google_service_account_iam_member" "key_admin" {
  member             = local.tf_principal
  role               = "roles/iam.serviceAccountKeyAdmin"
  service_account_id = var.service_account_id
}

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
    google_service_account_iam_member.key_admin
  ]
}
