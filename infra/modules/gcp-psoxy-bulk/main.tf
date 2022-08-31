terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }
}

# data input to function
resource "google_storage_bucket" "input-bucket" {
  project                     = var.project_id
  name                        = "psoxy-${var.source_kind}-input"
  location                    = var.region
  force_destroy               = true
  uniform_bucket_level_access = true
}

# data output from function
resource "google_storage_bucket" "output-bucket" {
  project                     = var.project_id
  name                        = "psoxy-${var.source_kind}-output"
  location                    = var.region
  force_destroy               = true
  uniform_bucket_level_access = true
}

resource "google_service_account" "service-account" {
  account_id   = "psoxy-${var.source_kind}"
  display_name = "Psoxy ${var.source_kind} service account for cloud function"
  description  = "Service account where the function is running and have permissions to read secrets"
  project      = var.project_id
}

resource "google_secret_manager_secret_iam_member" "salt-secret-access-for-service-account" {
  member    = "serviceAccount:${google_service_account.service-account.email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = var.salt_secret_id
}

resource "google_storage_bucket_iam_member" "access_for_import_bucket" {
  bucket = google_storage_bucket.input-bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.service-account.email}"
}

resource "google_storage_bucket_iam_member" "grant_sa_read_on_processed_bucket" {
  count = length(var.worklytics_sa_emails)

  bucket = google_storage_bucket.output-bucket.name
  member = "serviceAccount:${var.worklytics_sa_emails[count.index]}"
  role   = "roles/storage.objectViewer"
}



resource "google_storage_bucket_iam_member" "access_for_processed_bucket" {
  bucket = google_storage_bucket.output-bucket.name
  role   = var.bucket_write_role_id
  member = "serviceAccount:${google_service_account.service-account.email}"
}

resource "google_cloudfunctions_function" "function" {
  name        = "psoxy-${var.source_kind}"
  description = "Psoxy for ${var.source_kind} files"
  runtime     = "java11"
  project     = var.project_id
  region      = var.region

  available_memory_mb   = 1024
  source_archive_bucket = var.artifacts_bucket_name
  source_archive_object = var.deployment_bundle_object_name
  entry_point           = "co.worklytics.psoxy.GCSFileEvent"
  service_account_email = google_service_account.service-account.email

  environment_variables = merge(tomap({
    INPUT_BUCKET  = google_storage_bucket.input-bucket.name,
    OUTPUT_BUCKET = google_storage_bucket.output-bucket.name
    }),
    var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
    var.environment_variables
  )

  secret_environment_variables {
    key     = "PSOXY_SALT"
    secret  = var.salt_secret_id
    version = var.salt_secret_version_number
  }

  event_trigger {
    event_type = "google.storage.object.finalize"
    resource   = google_storage_bucket.input-bucket.name
  }

  depends_on = [
    google_secret_manager_secret_iam_member.salt-secret-access-for-service-account
  ]
}
