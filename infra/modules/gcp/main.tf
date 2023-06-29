
locals {
  # a prefix legal for GCP Roles
  environment_id_role_prefix = replace(var.environment_id_prefix, "-", "_")
}


# activate required GCP service APIs
# NOTE: used in lieu of 'google_project_services' because that resouce is *authorative*, so will
# disable other APIs that are enabled in the project - which may not be what we want if shared
# project, or if other services used to support (eg, monitoring APIs or somthing)
resource "google_project_service" "gcp-infra-api" {
  for_each = toset([
    "cloudbuild.googleapis.com", # some modes of Cloud Functions seem to need this, so TBD
    "cloudfunctions.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com", # manage IAM via terraform (as of 2023-04-17, internal dev envs didn't have this; so really needed?)
    "secretmanager.googleapis.com",
    # "serviceusage.googleapis.com", # manage service APIs via terraform (prob already
  ])

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false
  disable_on_destroy         = false # disabling on destroy has potential to conflict with other uses of the project
}

# pseudo secret
resource "google_secret_manager_secret" "pseudonymization-salt" {
  project   = var.project_id
  secret_id = "${var.config_parameter_prefix}PSOXY_SALT"
  labels    = var.default_labels

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
      replication, # can't change replication after creation
      labels
    ]
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
  secret_id = "${var.config_parameter_prefix}PSOXY_ENCRYPTION_KEY"
  labels    = var.default_labels

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
      replication, # can't change replication after creation
      labels
    ]
  }

  depends_on = [
    google_project_service.gcp-infra-api
  ]
}

resource "random_password" "pseudonymization-key" {
  length  = 32
  special = true
}

resource "google_secret_manager_secret_version" "pseudonymization-key_initial_version" {
  secret      = google_secret_manager_secret.pseudonymization-key.id
  secret_data = sensitive(random_password.pseudonymization-key.result)


  # if customer changes value outside TF, don't overwrite
  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}

module "psoxy_package" {
  source = "../psoxy-package"

  implementation     = "gcp"
  path_to_psoxy_java = "${var.psoxy_base_dir}java"
  deployment_bundle  = var.deployment_bundle
  force_bundle       = var.force_bundle
}

moved {
  from = module.psoxy-package
  to   = module.psoxy_package
}

# install test tool, if it exists in expected location
module "test_tool" {
  count = var.install_test_tool ? 1 : 0

  source = "../psoxy-test-tool"

  path_to_tools = "${var.psoxy_base_dir}tools"
  psoxy_version = module.psoxy_package.version
}

moved {
  from = module.test_tool
  to   = module.test_tool[0]
}

# GCP wants a zip containing a JAR; can't handle JAR directly - so create that here if no bundle
# was passed into module
# in effect, this is equivalent to shell command:
# zip /tmp/deployment_bundle.zip ${module.psoxy_package.path_to_deployment_jar}
data "archive_file" "source" {
  count = var.deployment_bundle == null ? 1 : 0

  type        = "zip"
  source_file = module.psoxy_package.path_to_deployment_jar
  output_path = "/tmp/deployment_bundle.zip" # NOTE: this is not writable location in Terraform cloud
}

# Create bucket that will host the source code
resource "google_storage_bucket" "artifacts" {
  project                     = var.project_id
  name                        = coalesce(var.custom_artifacts_bucket_name, "${var.project_id}-${var.environment_id_prefix}artifacts-bucket")
  location                    = var.bucket_location
  uniform_bucket_level_access = true
  force_destroy               = true
  labels                      = var.default_labels

  # TODO: remove in v0.5
  lifecycle {
    ignore_changes = [
      labels
    ]
  }
}

locals {
  file_name_with_sha1 = replace(module.psoxy_package.filename, ".jar",
  "_${filesha1(module.psoxy_package.path_to_deployment_jar)}.zip")

  # NOTE: not a coalesce, bc Terraform evaluates all expressions within coalesce() even if first is non-null
  bundle_path = var.deployment_bundle == null ? data.archive_file.source[0].output_path : var.deployment_bundle
}

# add zipped JAR to bucket
resource "google_storage_bucket_object" "function" {
  name           = "${var.environment_id_prefix}${local.file_name_with_sha1}"
  content_type   = "application/zip"
  bucket         = google_storage_bucket.artifacts.name
  source         = local.bundle_path
  detect_md5hash = true
}

resource "google_project_iam_custom_role" "bucket_write" {
  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}writeAccess"
  title       = "Access for writing and update objects in bucket"
  description = "Write and update support, because storage.objectCreator role only support creation - not update"

  permissions = [
    "storage.objects.create",
    "storage.objects.delete"
  ]
}

moved {
  from = google_project_iam_custom_role.bucket-write
  to   = google_project_iam_custom_role.bucket_write
}

resource "google_project_iam_custom_role" "psoxy_instance_secret_locker_role" {
  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}PsoxyInstanceSecretLocker"
  title       = "Access for updating and reading secrets"
  description = "Role to grant on secret that is to be managed by a Psoxy instance (cloud function); subset of roles/secretmanager.admin, to support reading/updating the secret"

  permissions = [
    "resourcemanager.projects.get",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.update"
  ]
}

output "artifacts_bucket_name" {
  value = google_storage_bucket.artifacts.name
}

output "deployment_bundle_object_name" {
  value = google_storage_bucket_object.function.name
}

output "bucket_write_role_id" {
  value = google_project_iam_custom_role.bucket_write.id
}

# Deprecated, it will be removed in v0.5.x
output "salt_secret_id" {
  value = google_secret_manager_secret.pseudonymization-salt.secret_id
}

# Deprecated, it will be removed in v0.5.x
output "salt_secret_version_number" {
  value = trimprefix(google_secret_manager_secret_version.initial_version.name, "${google_secret_manager_secret.pseudonymization-salt.name}/versions/")
}

output "secrets" {
  value = {
    PSOXY_ENCRYPTION_KEY = {
      secret_id      = google_secret_manager_secret.pseudonymization-key.secret_id,
      version_number = trimprefix(google_secret_manager_secret_version.pseudonymization-key_initial_version.name, "${google_secret_manager_secret.pseudonymization-key.name}/versions/")
    },
    PSOXY_SALT = {
      secret_id      = google_secret_manager_secret.pseudonymization-salt.secret_id,
      version_number = trimprefix(google_secret_manager_secret_version.initial_version.name, "${google_secret_manager_secret.pseudonymization-salt.name}/versions/")
    }
  }
}

output "version" {
  value = module.psoxy_package.version
}

output "filename" {
  value = module.psoxy_package.filename
}

output "path_to_deployment_jar" {
  description = "Path to the package to deploy (JAR)."
  value       = module.psoxy_package.path_to_deployment_jar
}

output "psoxy_instance_secret_locker_role_id" {
  value = google_project_iam_custom_role.psoxy_instance_secret_locker_role.id
}
