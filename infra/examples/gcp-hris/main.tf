terraform {
  required_providers {
    google = {
      version = ">= 3.74, <= 4.0"
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
  source = "../../modules/gcp"

  project_id          = google_project.psoxy-project.project_id
  invoker_sa_emails   = var.worklytics_sa_emails

  depends_on = [
    google_project.psoxy-project
  ]
}

module "psoxy-package" {
  source = "../../modules/psoxy-package"

  implementation     = "gcp"
  path_to_psoxy_java = "../../../java"
}

# Create bucket that will host the source code
resource "google_storage_bucket" "deployment_bucket" {
  name            = "${var.bucket_prefix}-function"
  location        = var.bucket_location
  force_destroy   = true
  project         = google_project.psoxy-project.project_id
}

# Add source code zip to bucket
resource "google_storage_bucket_object" "function" {
  # Append file MD5 to force bucket to be recreated
  name   = format("${module.psoxy-package.path_to_deployment_jar}#%s", formatdate("mmss", timestamp()))
  bucket = google_storage_bucket.deployment_bucket.name
  source = module.psoxy-package.path_to_deployment_jar
}

resource "google_storage_bucket" "import-bucket" {
  name                        = "${var.bucket_prefix}-import"
  location                    = var.bucket_location
  force_destroy               = true
  project         = google_project.psoxy-project.project_id
}

resource "google_storage_bucket" "processed-bucket" {
  name                        = "${var.bucket_prefix}-processed"
  location                    = var.bucket_location
  force_destroy               = true
  project         = google_project.psoxy-project.project_id
}

resource "google_cloudfunctions_function" "function" {
  name        = "psoxy-hris"
  description = "Psoxy for HRIS files"
  runtime     = "java11"

  available_memory_mb   = 128
  source_archive_bucket = google_storage_bucket.deployment_bucket.name
  source_archive_object = google_storage_bucket_object.function.name
  entry_point           = "co.worklytics.psoxy.GCSFileEvent"

  environment_variables = merge(tomap({
      INPUT_BUCKET  = google_storage_bucket.import-bucket.name,
      OUTPUT_BUCKET = google_storage_bucket.processed-bucket.name
    }), yamldecode(file("../../../configs/hris.yaml")))

  event_trigger {
    event_type = "google.storage.object.finalize"
    resource = google_storage_bucket.import-bucket.name
  }

  depends_on    = [
    google_storage_bucket.import-bucket,
    google_storage_bucket.processed-bucket
  ]
}
