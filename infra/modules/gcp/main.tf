
locals {
  # a prefix legal for GCP Roles
  environment_id_role_prefix = replace(var.environment_id_prefix, "-", "_")
}


# activate required GCP service APIs
# NOTE: used in lieu of 'google_project_services' because that resouce is *authorative*, so will
# disable other APIs that are enabled in the project - which may not be what we want if shared
# project, or if other services used to support (eg, monitoring APIs or somthing)
resource "google_project_service" "gcp_infra_api" {
  for_each = toset([
    "cloudbuild.googleapis.com", # some modes of Cloud Functions seem to need this, so TBD
    "cloudfunctions.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "compute.googleapis.com", # seems required w newer Google provider versions, for resources we use
    "iam.googleapis.com",     # manage IAM via terraform (as of 2023-04-17, internal dev envs didn't have this; so really needed?)
    "secretmanager.googleapis.com",
    # "serviceusage.googleapis.com", # manage service APIs via terraform (prob already
  ])

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false
  disable_on_destroy         = false # disabling on destroy has potential to conflict with other uses of the project
}

# pseudo secret
resource "google_secret_manager_secret" "pseudonym_salt" {
  project   = var.project_id
  secret_id = "${var.config_parameter_prefix}PSOXY_SALT"
  labels = merge(
    var.default_labels,
    {
      terraform_managed_value = true
    }
  )

  replication {
    user_managed {
      dynamic "replicas" {
        for_each = var.secret_replica_locations
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
    google_project_service.gcp_infra_api
  ]
}

# not really a 'password', but 'random_string' isn't "sensitive" by terraform, so
# is output to console
resource "random_password" "pseudonym_salt" {
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
  secret      = google_secret_manager_secret.pseudonym_salt.id
  secret_data = sensitive(random_password.pseudonym_salt.result)

  # if customer changes value outside TF, don't overwrite
  lifecycle {
    ignore_changes = [
      secret_data
    ]
  }
}

resource "google_secret_manager_secret" "pseudonymization_key" {
  project   = var.project_id
  secret_id = "${var.config_parameter_prefix}PSOXY_ENCRYPTION_KEY"
  labels = merge(
    var.default_labels,
    {
      terraform_managed_value = true
    }
  )

  replication {
    user_managed {
      dynamic "replicas" {
        for_each = var.secret_replica_locations
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
    google_project_service.gcp_infra_api
  ]
}

resource "random_password" "pseudonym_encryption_key" {
  length  = 32
  special = true
}

resource "google_secret_manager_secret_version" "pseudonym_encryption_key_initial_version" {
  secret      = google_secret_manager_secret.pseudonymization_key.id
  secret_data = sensitive(random_password.pseudonym_encryption_key.result)


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

locals {
  # NOTE: `try` needed here bc Terraform doesn't short-circuit boolean evaluation
  is_remote_bundle       = var.deployment_bundle != null && try(startswith(var.deployment_bundle, "gs://"), false)
  remote_bucket_name     = local.is_remote_bundle ? split("/", var.deployment_bundle)[2] : null
  remote_bundle_artifact = local.is_remote_bundle ? split("/", var.deployment_bundle)[3] : null

  file_name_with_sha1 = local.is_remote_bundle ? sha1(var.deployment_bundle) : replace(module.psoxy_package.filename, ".jar",
  "_${filesha1(module.psoxy_package.path_to_deployment_jar)}.zip")

  # NOTE: not a coalesce, bc Terraform evaluates all expressions within coalesce() even if first is non-null
  bundle_path = var.deployment_bundle == null ? data.archive_file.source[0].output_path : var.deployment_bundle
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
  count = local.is_remote_bundle ? 0 : 1

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

# add zipped JAR to bucket
resource "google_storage_bucket_object" "function" {
  count = local.is_remote_bundle ? 0 : 1

  name           = "${var.environment_id_prefix}${local.file_name_with_sha1}"
  content_type   = "application/zip"
  bucket         = google_storage_bucket.artifacts[0].name
  source         = local.bundle_path
  detect_md5hash = true
}

locals {
  artifact_bucket_name          = local.is_remote_bundle ? local.remote_bucket_name : google_storage_bucket.artifacts[0].name
  deployment_bundle_object_name = local.is_remote_bundle ? local.remote_bundle_artifact : google_storage_bucket_object.function[0].name
}

# install test tool, if it exists in expected location
module "test_tool" {
  count = var.install_test_tool ? 1 : 0

  source = "../psoxy-test-tool"

  path_to_tools = "${var.psoxy_base_dir}tools"
  psoxy_version = module.psoxy_package.version
}

# create custom role needed for bulk psoxy use-cases
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

# Deprecated; only keep to support old installations
resource "google_project_iam_custom_role" "psoxy_instance_secret_role" {
  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}PsoxyInstanceSecretHandler"
  title       = "Access for updating and reading secrets"
  description = "Role to grant on secret that is to be managed by a Psoxy instance (cloud function); subset of roles/secretmanager.admin, to support reading/updating the secret and managing their versions"

  permissions = [
    "resourcemanager.projects.get",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.update",
    "secretmanager.versions.add",
    "secretmanager.versions.access",
    "secretmanager.versions.destroy",
    "secretmanager.versions.disable",
    "secretmanager.versions.enable",
    "secretmanager.versions.get",
    "secretmanager.versions.list"
  ]
}

output "artifacts_bucket_name" {
  value = local.artifact_bucket_name
}

output "deployment_bundle_object_name" {
  value = local.deployment_bundle_object_name
}

output "bucket_write_role_id" {
  value = google_project_iam_custom_role.bucket_write.id
}

# Deprecated, it will be removed in v0.5.x
output "salt_secret_id" {
  value = google_secret_manager_secret.pseudonym_salt.secret_id
}

# Deprecated, it will be removed in v0.5.x
output "salt_secret_version_number" {
  value = trimprefix(google_secret_manager_secret_version.initial_version.name, "${google_secret_manager_secret.pseudonym_salt.name}/versions/")
}

output "secrets" {
  value = {
    PSOXY_ENCRYPTION_KEY = {
      secret_id      = google_secret_manager_secret.pseudonymization_key.secret_id,
      version_number = trimprefix(google_secret_manager_secret_version.pseudonym_encryption_key_initial_version.name, "${google_secret_manager_secret.pseudonymization_key.name}/versions/")
    },
    PSOXY_SALT = {
      secret_id      = google_secret_manager_secret.pseudonym_salt.secret_id,
      version_number = trimprefix(google_secret_manager_secret_version.initial_version.name, "${google_secret_manager_secret.pseudonym_salt.name}/versions/")
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
  value = google_project_iam_custom_role.psoxy_instance_secret_role.id
}

output "psoxy_instance_secret_role_id" {
  value = google_project_iam_custom_role.psoxy_instance_secret_role.id
}

output "pseudonym_salt" {
  description = "Value used to salt pseudonyms (SHA-256) hashes. If migrate to new deployment, you should copy this value."
  value       = random_password.pseudonym_salt.result
  sensitive   = true
}
