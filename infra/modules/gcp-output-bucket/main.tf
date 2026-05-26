# trivy:ignore:AVD-GCP-0077
resource "google_storage_bucket" "bucket" {
  project                     = var.project_id
  name                        = "${var.bucket_name_prefix}${var.bucket_name_suffix}"
  location                    = var.region
  force_destroy               = var.bucket_force_destroy
  uniform_bucket_level_access = true

  dynamic "logging" {
    for_each = var.bucket_access_logs_destination != null ? [var.bucket_access_logs_destination] : []
    content {
      log_bucket = logging.value
    }
  }

  versioning {
    enabled = var.enable_versioning
  }

  lifecycle_rule {
    condition {
      age = var.expiration_days
    }

    action {
      type = "Delete"
    }
  }

  # TODO: remove in v0.6 ??? NO - various exotic migration cases are much easier with this
  # left of 0.5, just to ease migrations; avoid destruction/recreation of bucket
  lifecycle {
    ignore_changes = [
      name, # due to name change from -output --> -sanitized, ignore name change to avoid recreating bucket
    ]
  }
}

resource "google_storage_bucket_iam_member" "write_to_output_bucket" {
  bucket = google_storage_bucket.bucket.name
  role   = var.bucket_write_role_id
  member = "serviceAccount:${var.function_service_account_email}"
}

resource "google_storage_bucket_iam_member" "accessors" {
  for_each = toset(var.sanitizer_accessor_principals)

  bucket = google_storage_bucket.bucket.name
  member = each.value
  role   = "roles/storage.objectViewer"

  # GCS IAM bindings do not support source-IP conditions on objectViewer; rely on proxy/app controls.
}

output "bucket_name" {
  value = google_storage_bucket.bucket.name
}

