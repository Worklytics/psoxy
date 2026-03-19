
locals {
  # a prefix legal for GCP Roles
  environment_id_role_prefix = replace(var.environment_id_prefix, "-", "_")

  # version of environment_id_prefix with trailing space, presuming it's a hyphen or a underscore
  environment_id_prefix_display = length(var.environment_id_prefix) > 0 ? "${substr(var.environment_id_prefix, 0, length(var.environment_id_prefix) - 1)} " : ""

  # additional services required for bulk mode
  services_required_for_bulk_mode = [
    "eventarc.googleapis.com", # required for eventarc triggers to functions gen2
    "pubsub.googleapis.com",   # needed for cloud run gen2
  ]

  # additional services required for webhook collectors
  services_required_for_webhook_collectors = [
    "cloudkms.googleapis.com",       # signing webhooks
    "cloudscheduler.googleapis.com", # triggering batches
    "pubsub.googleapis.com",         # webhooks batched via pubsub
  ]
}


# activate required GCP service APIs
# NOTE: used in lieu of 'google_project_services' because that resouce is *authorative*, so will
# disable other APIs that are enabled in the project - which may not be what we want if shared
# project, or if other services used to support (eg, monitoring APIs or somthing)
resource "google_project_service" "gcp_infra_api" {
  for_each = toset(concat([
    "artifactregistry.googleapis.com", # for GCP Artifact Registry, as required for new Cloud Functions since Feb 2024
    "cloudbuild.googleapis.com",       # some modes of Cloud Functions seem to need this, so TBD
    "cloudfunctions.googleapis.com",   # believe remains required for cloud run functions gen2
    "cloudresourcemanager.googleapis.com",
    "compute.googleapis.com", # seems required w newer Google provider versions, for resources we use
    "iam.googleapis.com",     # manage IAM via terraform (as of 2023-04-17, internal dev envs didn't have this; so really needed?)
    "run.googleapis.com",     # required for cloud run functions gen2
    "secretmanager.googleapis.com",
    "storage.googleapis.com", # required for both API and bulk modes, bc gcs used to stage bundles (artifacts) for function deployment
    # "serviceusage.googleapis.com", # manage service APIs via terraform (prob already
    ],
    var.support_bulk_mode ? local.services_required_for_bulk_mode : [],
    var.support_webhook_collectors ? local.services_required_for_webhook_collectors : [],
    local.provision_serverless_connector ? ["vpcaccess.googleapis.com"] : []
  ))

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false
  disable_on_destroy         = false # disabling on destroy has potential to conflict with other uses of the project
}

# TODO: This is will supported since 0.5 psoxy version, as google provider needs to be updated
/*resource "google_artifact_registry_repository" "psoxy-functions-repo" {
  location      = var.bucket_location
  project       = var.project_id
  repository_id = "psoxy-functions"
  description   = "Docker repository used on the cloud functions"
  format        = "DOCKER"

  ## Not supported in current google providers, needs 5.14 as there it is GA
  # See https://github.com/hashicorp/terraform-provider-google/blob/main/CHANGELOG.md#5140-jan-29-2024
  # but even is present in the documentation (https://registry.terraform.io/providers/hashicorp/google/4.80.0/docs/resources/artifact_registry_repository#argument-reference)
  # when applied it throws an error with the message: "An argument named "cleanup_policy_dry_run" is not expected here"
  # and "no block for cleanup_policies" is expected
  */ /*cleanup_policy_dry_run = false

  # https://cloud.google.com/artifact-registry/docs/repositories/cleanup-policy#json_2
  # https://registry.terraform.io/providers/hashicorp/google/4.80.0/docs/resources/artifact_registry_repository#argument-reference
  cleanup_policies {
    id     = "keep-most-recent-versions"
    action = "KEEP"

    most_recent_versions {
      keep_count = 3
    }
  }*/ /*

  depends_on = [
    google_project_service.gcp_infra_api
  ]
}*/

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
  force_destroy               = var.bucket_force_destroy
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

# create custom role needed for any proxy use-case that writes output to a GCS bucket:
# - bulk mode
# - webhook collectors
# - side output
# - async mode
# could condition creation of this on at least one of the above being used, but essentially EVERYONE uses at least one of those,
# so not bothering with unnecessary complexity of that.
resource "google_project_iam_custom_role" "bucket_write" {
  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}writeAccess"
  title       = "${local.environment_id_prefix_display}Bucket Object Write/Update"
  description = "Write and update support, because storage.objectCreator role only support creation - not update"

  permissions = [
    "storage.objects.create",
    "storage.objects.delete"
  ]
}


# to avoid error 'The Cloud Storage service account for your bucket is unable to publish to Cloud Pub/Sub topics in the specified project'
# see: https://cloud.google.com/eventarc/docs/run/quickstart-storage#before-you-begin
data "google_storage_project_service_account" "gcs_default_service_account" {
  project = var.project_id
}

resource "google_project_iam_member" "grant_gcs-sa_pub-sub-publisher" {
  count = var.support_bulk_mode && var.provision_project_level_iam ? 1 : 0

  project = var.project_id
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${data.google_storage_project_service_account.gcs_default_service_account.email_address}"
}

# Grant Cloud Build builder role to Compute Engine default service account
# Required for Cloud Functions Gen2 deployment to build the function
# See: https://cloud.google.com/functions/docs/troubleshooting#build-service-account
resource "google_project_iam_member" "grant_compute_default_sa_cloudbuild_builder" {
  count = var.provision_project_level_iam ? 1 : 0

  project = var.project_id
  role    = "roles/cloudbuild.builds.builder"
  member  = "serviceAccount:${data.google_compute_default_service_account.default.email}"
}


resource "google_project_iam_custom_role" "oidc_token_verifier" {
  count = var.support_webhook_collectors ? 1 : 0

  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}OIDCTokenVerifier"
  title       = "${local.environment_id_prefix_display} OIDC Token Verifier"
  description = "Role to verify OIDC tokens used to authenticate requests to the webhook collectors; grant this to GCP principals that need to verify OIDC tokens, on the KMS key that is used to sign the tokens"

  permissions = [
    "cloudkms.cryptoKeys.get",
    "cloudkms.cryptoKeyVersions.get",
    "cloudkms.cryptoKeyVersions.list",         # need to list versions, as possibly ANY enabled version might have been used to sign the token
    "cloudkms.cryptoKeyVersions.viewPublicKey" # need to view public key, to verify signature
  ]
}



# q: is there a default Cloud Scheduler service account, that needs tokencreator role on the webhook_batch_invoker SA?
# GCP docs don't show one, and ChatGPT didn't say one needed until I asked - at which point it gave me example email
# for the SA that doesn't look to follow usual pattern ...
resource "google_service_account" "webhook_batch_invoker" {
  count = var.support_webhook_collectors ? 1 : 0

  project      = var.project_id
  account_id   = "${var.environment_id_prefix}webhook-batch"
  display_name = "${local.environment_id_prefix_display} Webhook Batch Invoker"
  description  = "Service account that will invoke the batch processing of webhooks"
}

data "google_project" "project" {
  project_id = var.project_id
}

# Cloud Functions Gen2 deployment REQUIRES the terraform principal to have roles/iam.serviceAccountUser
# on the Compute Engine default service account in order to provision the Cloud Functions Gen2 instance
# with a specific service account (which we do, and seems like good practice)
data "google_compute_default_service_account" "default" {
  project = var.project_id
}

resource "google_service_account_iam_member" "tf_runner_act_as_compute_default" {
  service_account_id = data.google_compute_default_service_account.default.name
  member             = var.tf_runner_iam_principal
  role               = "roles/iam.serviceAccountUser"
}

# q: is this needed????
# doubt is, without it, what is allowing the scheduler to generate OIDC tokens on behalf of the webhook_batch_invoker SA?
# but admittedly, I'm unclear if it's this SA that needs the grant, or if  instead granting `roles/iam.serviceAccountUser`
# to the GCP principal terraform is running as is the proper approach (eg, idea is that terraform is 'scheduling' the job
# as the service account's identity)
resource "google_service_account_iam_member" "allow_scheduler_impersonation" {
  count = var.support_webhook_collectors ? 1 : 0

  service_account_id = google_service_account.webhook_batch_invoker[0].id
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:service-${data.google_project.project.number}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
}

# TODO: remove in v0.6.x
# Deprecated; only keep to support old installations
resource "google_project_iam_custom_role" "psoxy_instance_secret_role" {
  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}PsoxyInstanceSecretHandler"
  title       = "${local.environment_id_prefix_display}Instance Secret Handler"
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


# BEGIN Serverless VPC Access connector (conditional)
locals {
  MAX_SERVERLESS_CONNECTOR_NAME_LENGTH = 25

  vpc_defined = var.vpc_config != null

  provision_serverless_connector = local.vpc_defined && try(var.vpc_config.serverless_connector, null) == null
  legal_connector_prefix         = substr(var.environment_id_prefix, 0, local.MAX_SERVERLESS_CONNECTOR_NAME_LENGTH)
  legal_connector_suffix         = substr("connector", 0, max(0, local.MAX_SERVERLESS_CONNECTOR_NAME_LENGTH - length(var.environment_id_prefix)))

  # check if shared VPC
  vpc_connector_network_project = coalesce(
    try(regex("^projects/([^/]+)", var.vpc_config.network)[0], null),
  var.project_id)
  shared = var.project_id != local.vpc_connector_network_project

  # if shared, expect network, expect everything set-up

  # network argument to vpc_access_connector resource; must be provided if subnet isn't
  vpc_connector_network = try(local.shared || !local.vpc_defined ? null : var.vpc_config.network, null)

  # extract region from subnetwork (if shared)
  vpc_connector_region = coalesce(
    try(regex("projects/[^/]+/regions/([^/]+)", var.vpc_config.subnet)[0], null),
  var.gcp_region)

  vpc_connector_subnetwork_name = !local.provision_serverless_connector ? null : coalesce(
    try(regex(".*/([^/]+)$", var.vpc_config.subnet)[0], null),
  try(local.vpc_defined ? var.vpc_config.subnet : null, null))
}

resource "google_vpc_access_connector" "connector" {
  count = local.provision_serverless_connector ? 1 : 0

  project = var.project_id
  region  = local.vpc_connector_region
  name    = "${local.legal_connector_prefix}${local.legal_connector_suffix}"

  subnet {
    name       = local.vpc_connector_subnetwork_name
    project_id = local.vpc_connector_network_project
  }

}

locals {
  vpc_config = try(
    {
      serverless_connector = google_vpc_access_connector.connector[0].id
    },
    {
      serverless_connector = var.vpc_config.serverless_connector
    },
    null
  )
}
# END VPC (conditional)





locals {
  custom_testing_role_perms = concat(
    var.support_webhook_collectors ? [
      "cloudscheduler.jobs.get",
      "cloudscheduler.jobs.run",
    ] : [],
    []
  )
}

# Custom role to support testing, if needed

# Custom role for principals who need to test data sanitization (webhook collectors)
# Only created if webhook collectors are configured and testing infra is enabled
resource "google_project_iam_custom_role" "data_sanitization_tester" {
  count = var.provision_testing_infra && length(local.custom_testing_role_perms) > 0 ? 1 : 0

  project     = var.project_id
  role_id     = "${local.environment_id_role_prefix}DataSanitizationTester"
  title       = "${local.environment_id_prefix_display}Data Sanitization Tester"
  description = "Role for principals authorized to test data sanitization. Includes permissions for triggering Cloud Scheduler jobs."

  permissions = local.custom_testing_role_perms
}


# Grant test principals the custom role at project level
# Note: Cloud Scheduler doesn't support resource-level IAM, so project-level is required
resource "google_project_iam_member" "data_sanitization_tester_grant" {
  for_each = var.provision_testing_infra && var.support_webhook_collectors ? toset(var.gcp_principals_authorized_to_test) : toset([])

  project = var.project_id
  role    = google_project_iam_custom_role.data_sanitization_tester[0].id
  member  = each.key
}

## end custom testing infra



output "artifacts_bucket_name" {
  value = local.artifact_bucket_name
}

output "deployment_bundle_object_name" {
  value = local.deployment_bundle_object_name
}

output "bucket_write_role_id" {
  value       = google_project_iam_custom_role.bucket_write.id
  description = "Role to grant on bucket to enable writes. Used by any proxy use case that writes output to a GCS bucket."
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

output "artifact_repository" {
  #value = google_artifact_registry_repository.psoxy-functions-repo.id
  value = null # by default, GCP Artifact Registry will use "gcf-artifacts" repository

  depends_on = [
    # For ensuring the API is enabled, otherwise following error can happen:
    # "Error while updating cloudfunction [...] Cloud Functions uses Artifact Registry to store function docker images.
    # Artifact Registry API is not enabled in your project. To enable the API, visit [...]
    # or use the gcloud command 'gcloud services enable artifactregistry.googleapis.com' then retry.
    # If you enabled this API recently, wait a few minutes for the action to propagate to our systems and retry., forbidden"
    google_project_service.gcp_infra_api
  ]
}

output "oidc_token_verifier_role_id" {
  value       = try(google_project_iam_custom_role.oidc_token_verifier[0].id, null)
  description = "Role to grant on crypto key(s) used to sign OIDC tokens (used to authenticate requests to webhook collectors). Only provisioned if support_webhook_collectors is true."
}

output "webhook_batch_invoker_sa_email" {
  value = try(google_service_account.webhook_batch_invoker[0].email, null)
}

output "vpc_config" {
  value       = local.vpc_config
  description = "VPC configuration for the Cloud Run function. Possibly 'null' if no VPC is configured."
}

output "data_sanitization_tester_role_id" {
  value       = try(google_project_iam_custom_role.data_sanitization_tester[0].id, null)
  description = "Custom role for test principals. Includes permissions needed for end-to-end testing."
}
