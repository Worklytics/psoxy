
resource "google_storage_bucket" "bucket" {
  project                     = var.project_id
  name                        = "${var.bucket_name_prefix}${var.bucket_name_suffix}"
  location                    = var.region
  force_destroy               = true
  uniform_bucket_level_access = true

  lifecycle_rule {
    condition {
      age = var.expiration_days
    }

    action {
      type = "Delete"
    }
  }

  # TODO: remove in v0.5
  lifecycle {
    ignore_changes = [
      labels
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
}

output "bucket_name" {
  value = google_storage_bucket.bucket.name
}
