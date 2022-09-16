terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }
}

locals {
  function_name = "psoxy-${substr(var.source_kind, 0, 24)}"
}

data "google_project" "project" {
  project_id = var.project_id
}

resource "random_string" "bucket_id_part" {
  length  = 8
  special = false
  lower   = true
  upper   = false
  numeric = true
}

# data input to function
resource "google_storage_bucket" "input-bucket" {
  project                     = var.project_id
  name                        = "${local.function_name}-${random_string.bucket_id_part.id}-input"
  location                    = var.region
  force_destroy               = true
  uniform_bucket_level_access = true

  lifecycle {
    ignore_changes = [
      labels
    ]
  }
}

# data output from function
resource "google_storage_bucket" "output-bucket" {
  project                     = var.project_id
  name                        = "${local.function_name}-${random_string.bucket_id_part.id}-output"
  location                    = var.region
  force_destroy               = true
  uniform_bucket_level_access = true

  lifecycle {
    ignore_changes = [
      labels
    ]
  }
}

resource "google_service_account" "service-account" {
  project      = var.project_id
  account_id   = local.function_name
  display_name = "Psoxy Connector - ${var.source_kind}"
  description  = "${local.function_name} runs as this service account"
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

locals {
  secret_bindings = merge({
    PSOXY_SALT = {
      secret_id      = var.salt_secret_id
      version_number = var.salt_secret_version_number
    }
  }, var.secret_bindings)
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = local.secret_bindings

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${google_service_account.service-account.email}"
  role      = "roles/secretmanager.secretAccessor"
}

resource "google_cloudfunctions_function" "function" {
  name        = local.function_name
  description = "Psoxy instance to process ${var.source_kind} files"
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

  dynamic "secret_environment_variables" {
    for_each = local.secret_bindings
    iterator = secret_environment_variable

    content {
      key        = secret_environment_variable.key
      project_id = data.google_project.project.number
      secret     = secret_environment_variable.value.secret_id
      version    = secret_environment_variable.value.version_number
    }
  }


  event_trigger {
    event_type = "google.storage.object.finalize"
    resource   = google_storage_bucket.input-bucket.name
  }


  lifecycle {
    ignore_changes = [
      labels
    ]
  }

  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret
  ]
}
