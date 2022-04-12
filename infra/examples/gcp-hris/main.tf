terraform {
  required_providers {
    google = {
      version = "4.12.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

# NOTE: if you don't have perms to provision a GCP project in your billing account, you can have
# someone else create one and than import it:
#  `terraform import google_project.psoxy-project your-psoxy-project-id`
# either way, we recommend the project be used exclusively to host psoxy instances corresponding to
# a single worklytics account
resource "google_project" "psoxy-project" {
  name            = "Psoxy - ${var.environment_name}"
  project_id      = var.project_id
  folder_id       = var.folder_id
  billing_account = var.billing_account_id
}

module "psoxy-gcp" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/gcp"

  project_id        = google_project.psoxy-project.project_id
  invoker_sa_emails = var.worklytics_sa_emails

  depends_on = [
    google_project.psoxy-project
  ]
}

module "psoxy-package" {
  source = "git::https://github.com/worklytics/psoxy//infra/modules/psoxy-package"

  implementation     = "gcp"
  path_to_psoxy_java = "../../../java"
}

data "archive_file" "source" {
  type        = "zip"
  source_file = module.psoxy-package.path_to_deployment_jar
  output_path = "/tmp/function.zip"
}

# Create bucket that will host the source code
resource "google_storage_bucket" "deployment_bucket" {
  name          = "${var.bucket_prefix}-function"
  location      = var.bucket_location
  force_destroy = true
  project       = google_project.psoxy-project.project_id
}

# Add source code zip to bucket
resource "google_storage_bucket_object" "function" {
  # Append file MD5 to force bucket to be recreated
  name         = format("${module.psoxy-package.filename}#%s", formatdate("mmss", timestamp()))
  content_type = "application/zip"
  bucket       = google_storage_bucket.deployment_bucket.name
  source       = data.archive_file.source.output_path
}

# data input to function
resource "google_storage_bucket" "input-bucket" {
  project       = google_project.psoxy-project.project_id
  name          = "${var.bucket_prefix}-import"
  location      = var.bucket_location
  force_destroy = true
}

# data output from function
resource "google_storage_bucket" "output-bucket" {
  project       = google_project.psoxy-project.project_id
  name          = "${var.bucket_prefix}-processed"
  location      = var.bucket_location
  force_destroy = true
}

resource "google_service_account" "service-account" {
  account_id   = "psoxy-hris"
  display_name = "Psoxy HRIS service account for cloud function"
  description  = "Service account where the function is running and have permissions to read secrets"
  project      = var.project_id
}

resource "google_secret_manager_secret_iam_member" "salt-secret-acces-for-service-account" {
  member    = "serviceAccount:${google_service_account.service-account.email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = module.psoxy-gcp.salt_secret_name
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

resource "google_project_iam_custom_role" "bucket-write" {
  role_id     = "writeAccess"
  project     = google_project.psoxy-project.project_id
  title       = "Access for writing and update objects in bucket"
  description = "Write and update support, because storage.objectCreator role only support creation -not update"
  permissions = ["storage.objects.create", "storage.objects.delete"]
}

resource "google_storage_bucket_iam_member" "access_for_processed_bucket" {
  bucket = google_storage_bucket.output-bucket.name
  role   = google_project_iam_custom_role.bucket-write.id
  member = "serviceAccount:${google_service_account.service-account.email}"
}

resource "google_cloudfunctions_function" "function" {
  name        = "psoxy-hris"
  description = "Psoxy for HRIS files"
  runtime     = "java11"
  project     = google_project.psoxy-project.project_id
  region      = var.region

  available_memory_mb   = 1024
  source_archive_bucket = google_storage_bucket.deployment_bucket.name
  source_archive_object = google_storage_bucket_object.function.name
  entry_point           = "co.worklytics.psoxy.GCSFileEvent"
  service_account_email = google_service_account.service-account.email

  environment_variables = merge(tomap({
    INPUT_BUCKET  = google_storage_bucket.input-bucket.name,
    OUTPUT_BUCKET = google_storage_bucket.output-bucket.name
  }), yamldecode(file("../../../configs/hris.yaml")))

  secret_environment_variables {
    key     = "PSOXY_SALT"
    secret  = "PSOXY_SALT"
    version = module.psoxy-gcp.salt_secret_version_number
  }

  event_trigger {
    event_type = "google.storage.object.finalize"
    resource   = google_storage_bucket.input-bucket.name
  }

  depends_on = [
    google_storage_bucket.input-bucket,
    google_storage_bucket.output-bucket,
    google_service_account.service-account,
    google_secret_manager_secret_iam_member.salt-secret-acces-for-service-account
  ]
}
