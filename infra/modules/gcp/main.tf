


# activate required GCP service APIs
resource "google_project_service" "gcp-infra-api" {
  for_each = toset([
    "cloudbuild.googleapis.com",
    "cloudfunctions.googleapis.com",
    #"iam.googleapis.com", # manage IAM via terraform
    "secretmanager.googleapis.com",
    # "cloudbuild.googleapis.com", # some modes of Cloud Functions seem to need this, so TBD
    # "dlp.googleapis.com", # Data Loss Prevention API; if in v2 we support using this to filter with AI
  ])

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false

}

# pseudo secret
resource "google_secret_manager_secret" "pseudonymization-salt" {
  project   = var.project_id
  secret_id = "PSOXY_SALT"

  replication {
    automatic = true
  }

  depends_on = [
    google_project_service.gcp-infra-api
  ]
}

# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "random" {
  length  = 20
  special = true
}

# initial random salt to use; if you DON'T want this in your Terraform state, create a new version
# via some other means (eg, directly in GCP console). this should be done BEFORE your psoxy
# instance pseudonymizes anything; if salt is changed later, pseudonymization output will differ so
# previously pseudonymized data will be inconsistent with data pseudonymized after the change.
#
# To be clear, possession of salt alone doesn't let someone reverse pseudonyms.
resource "google_secret_manager_secret_version" "initial_version" {
  secret      = google_secret_manager_secret.pseudonymization-salt.id
  secret_data = sensitive(random_password.random.result)

  # if customer changes value outside TF, don't overwrite
  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}


resource "google_secret_manager_secret" "pseudonymization-key" {
  project   = var.project_id
  secret_id = "PSOXY_ENCRYPTION_KEY"

  replication {
    automatic = true
  }

  depends_on = [
    google_project_service.gcp-infra-api
  ]
}

resource "random_password" "pseudonymization-key" {
  length  = 32
  special = true
}

resource "google_secret_manager_secret_version" "initial_version" {
  secret      = google_secret_manager_secret.pseudonymization-key.id
  secret_data = sensitive(random_password.pseudonymization-key.result)


  # if customer changes value outside TF, don't overwrite
  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}



# grants invoker to these SA for ALL functions in this project. this is the recommended setup, as
# we expect this GCP project to only be used of psoxy instances to be consumed from your Worklytics
# account; otherwise, you can grant this role on specific functions
resource "google_project_iam_member" "grant_cloudFunctionInvoker_to_service_accounts" {
  for_each = toset(var.invoker_sa_emails)

  project = var.project_id
  member  = "serviceAccount:${each.value}"
  role    = "roles/cloudfunctions.invoker"
}

module "psoxy-package" {
  source = "../psoxy-package"

  implementation     = "gcp"
  path_to_psoxy_java = "${var.psoxy_base_dir}java"

}

data "archive_file" "source" {
  type        = "zip"
  source_file = module.psoxy-package.path_to_deployment_jar
  output_path = "/tmp/function.zip"
}

# Create bucket that will host the source code
resource "google_storage_bucket" "artifacts" {
  project       = var.project_id
  name          = "${var.project_id}-psoxy-artifacts-bucket"
  location      = var.bucket_location
  force_destroy = true
}

# Add source code zip to bucket
resource "google_storage_bucket_object" "function" {
  # Append file MD5 to force bucket to be recreated
  name         = format("${module.psoxy-package.filename}#%s", formatdate("mmss", timestamp()))
  content_type = "application/zip"
  bucket       = google_storage_bucket.artifacts.name
  source       = data.archive_file.source.output_path
}


