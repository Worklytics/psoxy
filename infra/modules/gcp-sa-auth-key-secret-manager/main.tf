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

resource "google_secret_manager_secret" "service-account-key" {
  project   = var.secret_project
  secret_id = var.secret_id

  replication {
    user_managed {
      dynamic "replicas" {
        for_each = var.replica_regions
        content {
          location = replicas.value
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      replication, # for backwards compatibility; replication can't be changed after secrets created
      labels
    ]
  }
}

resource "google_secret_manager_secret_version" "service-account-key-version" {
  secret      = google_secret_manager_secret.service-account-key.id
  secret_data = google_service_account_key.key.private_key

  lifecycle {
    create_before_destroy = true
  }
}
