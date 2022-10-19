# infra to support authentication of a service as a Google Workspace connector using a service
# account key
# this is only one approach to authentication; others may be more appropriate to your use-case.
# note this requires the terraform to be run regularly
# q: even a worthwhile module? it's just a key with rotation ...

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
