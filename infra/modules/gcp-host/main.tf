terraform {
  required_version = ">= 1.6, < 2.0" # should work with 1.6, but we don't automate testing against that version anymore
}

# constants
locals {
  SA_NAME_MIN_LENGTH = 6
  SA_NAME_MAX_LENGTH = 30
}

locals {
  default_config_parameter_prefix       = length(var.environment_name) == 0 ? "psoxy_" : "${var.environment_name}_"
  config_parameter_prefix               = var.config_parameter_prefix == "" ? local.default_config_parameter_prefix : var.config_parameter_prefix
  environment_id_prefix                 = "${var.environment_name}${length(var.environment_name) > 0 ? "-" : ""}"
  environment_id_display_name_qualifier = length(var.environment_name) > 0 ? " ${var.environment_name} " : ""

  api_connector_rules_files = merge(var.custom_api_connector_rules, { for k, v in var.api_connectors : k => v.rules_file if v.rules_file != null })
}

# TODO: probably pull all the way to the top level bc 1) proper tf style, 2) simplifies customization if it doesn't work for a particular environment
module "tf_runner" {
  source = "../../modules/gcp-tf-runner"

  tf_gcp_principal_email = var.tf_gcp_principal_email
}

module "psoxy" {
  source = "../../modules/gcp"

  project_id                   = var.gcp_project_id
  gcp_region                   = var.gcp_region
  environment_id_prefix        = local.environment_id_prefix
  psoxy_base_dir               = var.psoxy_base_dir
  deployment_bundle            = var.deployment_bundle
  force_bundle                 = var.force_bundle
  bucket_location              = var.gcp_region
  config_parameter_prefix      = local.config_parameter_prefix
  install_test_tool            = var.install_test_tool
  custom_artifacts_bucket_name = var.custom_artifacts_bucket_name
  default_labels               = var.default_labels
  support_bulk_mode            = length(var.bulk_connectors) > 0
  support_webhook_collectors   = length(var.webhook_collectors) > 0
  vpc_config                   = var.vpc_config
  bucket_force_destroy         = var.bucket_force_destroy
  tf_runner_iam_principal      = module.tf_runner.iam_principal
  provision_project_level_iam  = var.provision_project_level_iam
}


# BEGIN API CONNECTORS

resource "google_service_account" "api_connectors" {
  for_each = var.api_connectors

  project      = var.gcp_project_id
  account_id   = substr("${local.sa_prefix}${replace(each.key, "_", "-")}", 0, local.SA_NAME_MAX_LENGTH)
  display_name = "${local.environment_id_display_name_qualifier} ${each.key} API Connector Cloud Function"
  description  = "Service account that cloud function for ${each.key} API Connector will run as"
}

locals {
  secrets_to_provision = {
    for k, v in var.api_connectors :
    k => {
      for var_def in v.secured_variables :
      # TODO: in v0.5, the prefix with the instance_id can be removed
      "${replace(upper(var_def.name), "-", "_")}" =>
      merge({
        instance_id        = k
        instance_secret_id = "${replace(upper(k), "-", "_")}_${replace(upper(var_def.name), "-", "_")}"
        value              = "TODO: fill me"
        description        = ""
        },
      var_def)
    }
  }

  secrets_writable_by_instance = flatten([
    for instance_id, secrets in local.secrets_to_provision :
    [for secret_id, secret in values(secrets) : secret if secret.lockable || secret.writable]
  ])

  secrets_bound_as_env_vars = {
    for instance_id, secrets in local.secrets_to_provision :
    instance_id => {
      for secret_name, secret in secrets :
      secret_name => module.secrets[instance_id].secret_bindings[secret_name] if secret.value_managed_by_tf && !secret.lockable && !secret.writable
    }
  }

  # eg, neither writable, nor suitable to bind as env var (as GCP cloud function won't start if can't
  # read a value for something that's bound as an env var)
  secrets_access_only_but_not_managed_by_terraform = flatten([
    for instance_id, secrets in local.secrets_to_provision :
    [for secret_id, secret in values(secrets) : secret if !secret.value_managed_by_tf && !secret.lockable && !secret.writable]
  ])
}

module "secrets" {
  for_each = var.api_connectors

  source = "../../modules/gcp-secrets"

  secret_project    = var.gcp_project_id
  path_prefix       = "${local.config_parameter_prefix}${replace(upper(each.key), "-", "_")}_"
  secrets           = local.secrets_to_provision[each.key]
  default_labels    = var.default_labels
  replica_locations = var.secret_replica_locations
}

resource "google_secret_manager_secret_iam_member" "grant_sa_secretVersionManager_on_writable_secret" {
  for_each = { for secret in local.secrets_writable_by_instance : secret.instance_secret_id => secret }

  project   = var.gcp_project_id
  secret_id = "${local.config_parameter_prefix}${each.value.instance_secret_id}"
  member    = "serviceAccount:${google_service_account.api_connectors[each.value.instance_id].email}"
  role      = module.psoxy.psoxy_instance_secret_role_id

  depends_on = [
    module.secrets
  ]
}

resource "google_secret_manager_secret_iam_member" "grant_sa_secretAccessor_on_non_tf_secret" {
  for_each = { for secret in local.secrets_access_only_but_not_managed_by_terraform : secret.instance_secret_id => secret }

  project   = var.gcp_project_id
  secret_id = "${local.config_parameter_prefix}${each.value.instance_secret_id}"
  member    = "serviceAccount:${google_service_account.api_connectors[each.value.instance_id].email}"
  role      = "roles/secretmanager.secretAccessor"

  depends_on = [
    module.secrets
  ]
}



locals {
  # sa account_ids must be at least 6 chars long; if api_connector keys are short, and environment_name
  # is also short (or empty), keys alone might not be long enough; so prepend in such cases

  # distinguishes SA for Cloud Functions from SAs for connector OAuth Clients
  function_qualifier = "fn-"

  default_sa_prefix      = "${local.environment_id_prefix}${local.function_qualifier}"
  long_default_sa_prefix = "psoxy-${local.environment_id_prefix}${local.function_qualifier}"

  sa_prefix = length(local.default_sa_prefix) < local.SA_NAME_MIN_LENGTH ? local.long_default_sa_prefix : local.default_sa_prefix


  custom_original_side_outputs = { for k, v in var.custom_side_outputs :
    k => { bucket = v.ORIGINAL, allowed_readers = [] } if v.ORIGINAL != null
  }
  custom_sanitized_side_outputs = { for k, v in var.custom_side_outputs :
    k => { bucket = v.SANITIZED, allowed_readers = [] } if v.SANITIZED != null
  }
  required_side_output_config = {
    bucket          = null
    allowed_readers = [for v in var.worklytics_sa_emails : "serviceAccount:${v}"]
  }

  sanitized_side_outputs = { for k, v in var.api_connectors :
    k => try(v.enable_side_output, false) ? local.required_side_output_config : try(local.custom_sanitized_side_outputs[k], null)
  }
}

module "api_connector" {
  for_each = var.api_connectors

  source = "../../modules/gcp-psoxy-rest"

  project_id                            = var.gcp_project_id
  region                                = var.gcp_region
  source_kind                           = each.value.source_kind
  environment_id_prefix                 = local.environment_id_prefix
  instance_id                           = each.key
  service_account_email                 = google_service_account.api_connectors[each.key].email
  artifacts_bucket_name                 = module.psoxy.artifacts_bucket_name
  deployment_bundle_object_name         = module.psoxy.deployment_bundle_object_name
  artifact_repository_id                = module.psoxy.artifact_repository
  vpc_config                            = module.psoxy.vpc_config
  path_to_config                        = null
  path_to_repo_root                     = var.psoxy_base_dir
  example_api_calls                     = each.value.example_api_calls
  example_api_requests                  = each.value.example_api_requests
  example_api_calls_user_to_impersonate = each.value.example_api_calls_user_to_impersonate
  todo_step                             = var.todo_step
  target_host                           = each.value.target_host
  source_auth_strategy                  = each.value.source_auth_strategy
  oauth_scopes                          = try(each.value.oauth_scopes_needed, [])
  config_parameter_prefix               = local.config_parameter_prefix
  invoker_sa_emails                     = var.worklytics_sa_emails
  default_labels                        = var.default_labels
  gcp_principals_authorized_to_test     = var.gcp_principals_authorized_to_test
  bucket_write_role_id                  = module.psoxy.bucket_write_role_id
  side_output_original                  = try(local.custom_original_side_outputs[each.key], null)
  side_output_sanitized                 = try(local.sanitized_side_outputs[each.key], null)
  enable_async_processing               = try(each.value.enable_async_processing, false)
  todos_as_local_files                  = var.todos_as_local_files
  tf_runner_iam_principal               = module.tf_runner.iam_principal


  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME        = module.psoxy.filename
      IS_DEVELOPMENT_MODE    = contains(var.non_production_connectors, each.key)
      PSEUDONYMIZE_APP_IDS   = tostring(var.pseudonymize_app_ids)
      CUSTOM_RULES_SHA       = try(local.api_connector_rules_files[each.key], null) != null ? filesha1(local.api_connector_rules_files[each.key]) : null
      EMAIL_CANONICALIZATION = var.email_canonicalization
    }
  )

  secret_bindings = merge(
    local.secrets_bound_as_env_vars[each.key],
    module.psoxy.secrets,
    module.psoxy.artifact_repository
  )
}

module "custom_api_connector_rules" {
  for_each = local.api_connector_rules_files

  source = "../../modules/gcp-sm-rules"

  project_id        = var.gcp_project_id
  prefix            = "${local.config_parameter_prefix}${upper(replace(each.key, "-", "_"))}_"
  file_path         = each.value
  default_labels    = var.default_labels
  instance_sa_email = module.api_connector[each.key].service_account_email
}
# END API CONNECTORS

# BEGIN WEBHOOK COLLECTORS
locals {
  webhook_collectors_needing_keys = { for k, v in var.webhook_collectors : k => v if v.provision_auth_key != null }
  key_ring_needed                 = var.kms_key_ring == null && length(local.webhook_collectors_needing_keys) > 0
}

# key ring on which to provision required KMS keys; atm, only needed to support webhook collector case, but not
# necessarily exclusive to that use-case
# it's just a single resource, couple to single mode ... pushing it down in into the 'gcp' module would add a bunch of
# variables/outputs, as well as going against the general "inversion of control / composition" patterned preferred by terraform
resource "google_kms_key_ring" "proxy_key_ring" {
  count = local.key_ring_needed ? 1 : 0

  project  = var.gcp_project_id
  name     = replace(replace(var.environment_name, "/[^a-zA-Z0-9_-]/", "-"), "/-+/", "-")
  location = var.gcp_region
}

resource "google_service_account" "webhook_collector" {
  for_each = var.webhook_collectors

  project      = var.gcp_project_id
  account_id   = substr("${local.sa_prefix}${replace(each.key, "_", "-")}", 0, local.SA_NAME_MAX_LENGTH)
  display_name = "${local.environment_id_display_name_qualifier} ${each.key} Webhook Collector"
  description  = "Service account that cloud run function for ${each.key} Webhook Collector will run as"
}

module "webhook_collector" {
  for_each = var.webhook_collectors

  source = "../../modules/gcp-webhook-collector"

  project_id                         = var.gcp_project_id
  region                             = var.gcp_region
  environment_id_prefix              = local.environment_id_prefix
  instance_id                        = each.key
  service_account_email              = google_service_account.webhook_collector[each.key].email
  artifacts_bucket_name              = module.psoxy.artifacts_bucket_name
  deployment_bundle_object_name      = module.psoxy.deployment_bundle_object_name
  artifact_repository_id             = module.psoxy.artifact_repository
  path_to_repo_root                  = var.psoxy_base_dir
  config_parameter_prefix            = local.config_parameter_prefix
  invoker_sa_emails                  = var.worklytics_sa_emails
  vpc_config                         = module.psoxy.vpc_config
  default_labels                     = var.default_labels
  gcp_principals_authorized_to_test  = var.gcp_principals_authorized_to_test
  bucket_write_role_id               = module.psoxy.bucket_write_role_id
  side_output_original               = try(local.custom_original_side_outputs[each.key], null)
  side_output_sanitized              = try(local.sanitized_side_outputs[each.key], null)
  todos_as_local_files               = var.todos_as_local_files
  tf_runner_iam_principal            = module.tf_runner.iam_principal
  key_ring_id                        = local.key_ring_needed ? google_kms_key_ring.proxy_key_ring[0].id : var.kms_key_ring
  oidc_token_verifier_role_id        = module.psoxy.oidc_token_verifier_role_id
  provision_auth_key                 = each.value.provision_auth_key
  rules_file                         = each.value.rules_file
  webhook_batch_invoker_sa_email     = module.psoxy.webhook_batch_invoker_sa_email
  batch_processing_frequency_minutes = try(each.value.batch_processing_frequency_minutes, 5)
  output_path_prefix                 = each.value.output_path_prefix
  example_identity                   = try(each.value.example_identity, null)
  example_payload                    = try(each.value.example_payload, null)

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      BUNDLE_FILENAME        = module.psoxy.filename
      IS_DEVELOPMENT_MODE    = contains(var.non_production_connectors, each.key)
      EMAIL_CANONICALIZATION = var.email_canonicalization
    }
  )

  secret_bindings = module.psoxy.secrets


}

# END WEBHOOK COLLECTORS

# BEGIN BULK CONNECTORS
module "bulk_connector" {
  for_each = var.bulk_connectors

  source = "../../modules/gcp-psoxy-bulk"

  project_id                    = var.gcp_project_id
  region                        = var.gcp_region
  environment_id_prefix         = local.environment_id_prefix
  instance_id                   = each.key
  worklytics_sa_emails          = var.worklytics_sa_emails
  config_parameter_prefix       = local.config_parameter_prefix
  source_kind                   = each.value.source_kind
  artifacts_bucket_name         = module.psoxy.artifacts_bucket_name
  artifact_repository_id        = module.psoxy.artifact_repository
  deployment_bundle_object_name = module.psoxy.deployment_bundle_object_name
  psoxy_base_dir                = var.psoxy_base_dir
  bucket_write_role_id          = module.psoxy.bucket_write_role_id
  secret_bindings               = module.psoxy.secrets
  # Note: bulk connectors don't need VPC config - they only access GCS and Secret Manager
  example_file                      = try(each.value.example_file, null)
  instructions_template             = try(each.value.instructions_template, null)
  input_expiration_days             = var.bulk_input_expiration_days
  sanitized_expiration_days         = var.bulk_sanitized_expiration_days
  input_bucket_name                 = try(each.value.input_bucket_name, null)
  sanitized_bucket_name             = try(each.value.sanitized_bucket_name, null)
  default_labels                    = var.default_labels
  todos_as_local_files              = var.todos_as_local_files
  tf_runner_iam_principal           = module.tf_runner.iam_principal
  available_memory_mb               = coalesce(try(var.custom_bulk_connector_arguments[each.key].available_memory_mb, null), try(each.value.available_memory_mb, null), 512)
  timeout_seconds                   = coalesce(try(var.custom_bulk_connector_arguments[each.key].timeout_seconds, null), try(each.value.timeout_seconds, null), 540) # TODO: bump to 1800 (30 minutes) in 0.6.x
  gcp_principals_authorized_to_test = var.gcp_principals_authorized_to_test
  bucket_force_destroy              = var.bucket_force_destroy

  environment_variables = merge(
    var.general_environment_variables,
    try(each.value.environment_variables, {}),
    {
      SOURCE                 = each.value.source_kind
      RULES                  = each.value.rules_file == null ? yamlencode(try(var.custom_bulk_connector_rules[each.key], each.value.rules)) : file(each.value.rules_file)
      BUNDLE_FILENAME        = module.psoxy.filename
      IS_DEVELOPMENT_MODE    = contains(var.non_production_connectors, each.key)
      EMAIL_CANONICALIZATION = var.email_canonicalization
    }
  )

  depends_on = [
    module.psoxy # some of the set-up IAM grants done there, but not EXPLICITLY passed out as outputs and into above as inputs, are required; so make this explicit
  ]
}

# END BULK CONNECTORS

# BEGIN LOOKUP TABLES
module "lookup_output" {
  for_each = var.lookup_tables

  source = "../../modules/gcp-output-bucket"

  bucket_write_role_id           = module.psoxy.bucket_write_role_id
  function_service_account_email = module.bulk_connector[each.value.source_connector_id].instance_sa_email
  project_id                     = var.gcp_project_id
  region                         = var.gcp_region
  bucket_name_prefix             = module.bulk_connector[each.value.source_connector_id].bucket_prefix
  bucket_name_suffix             = "-lookup" # TODO: what if multiple lookups from same source??
  expiration_days                = each.value.expiration_days
  sanitizer_accessor_principals  = each.value.sanitized_accessor_principals
  bucket_labels                  = var.default_labels
  bucket_force_destroy           = var.bucket_force_destroy
}

locals {
  inputs_to_build_lookups_for = toset(distinct([for k, v in var.lookup_tables : v.source_connector_id]))
}

# TODO: this would be cleaner as env var, but creates a cycle:
# Error: Cycle: module.psoxy.module.psoxy-bulk.local_file.todo-gcp-psoxy-bulk-test, module.psoxy.module.lookup_output.var.function_service_account_email (expand), module.psoxy.module.lookup_output.google_storage_bucket_iam_member.write_to_output_bucket, module.psoxy.module.lookup_output.output.bucket_name (expand), module.psoxy.module.lookup_output.var.bucket_name_prefix (expand), module.psoxy.module.lookup_output.google_storage_bucket.bucket, module.psoxy.module.lookup_output.google_storage_bucket_iam_member.accessors, module.psoxy.module.lookup_output (close), module.psoxy.module.psoxy-bulk.var.environment_variables (expand), module.psoxy.module.psoxy-bulk.google_cloudfunctions_function.function, module.psoxy.module.psoxy-bulk (close)
resource "google_secret_manager_secret" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  project   = var.gcp_project_id
  secret_id = "${local.config_parameter_prefix}${upper(replace(each.key, "-", "_"))}_ADDITIONAL_TRANSFORMS"
  labels    = var.default_labels

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
      labels
    ]
  }
}

resource "google_secret_manager_secret_version" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  secret = google_secret_manager_secret.additional_transforms[each.key].name
  secret_data = yamlencode([
    for k, v in var.lookup_tables : {
      destinationBucketName : module.lookup_output[k].bucket_name
      rules : {
        columnsToDuplicate : {
          (v.join_key_column) : "${v.join_key_column}_pseudonym"
        },
        columnsToPseudonymize : ["${v.join_key_column}_pseudonym"]
        columnsToInclude : try(concat(v.columns_to_include, [
          v.join_key_column, "${v.join_key_column}_pseudonym"
        ]), null)
      }
      compressOutput : v.compress_output
    } if v.source_connector_id == each.key
  ])
}



# Needs to list versions, to find most recent
resource "google_secret_manager_secret_iam_member" "additional_transforms_viewer" {
  for_each = local.inputs_to_build_lookups_for

  secret_id = google_secret_manager_secret.additional_transforms[each.key].id
  member    = "serviceAccount:${module.bulk_connector[each.key].instance_sa_email}"
  role      = "roles/secretmanager.viewer"
}

# needs to access payload of the versions
resource "google_secret_manager_secret_iam_member" "additional_transforms" {
  for_each = local.inputs_to_build_lookups_for

  secret_id = google_secret_manager_secret.additional_transforms[each.key].id
  member    = "serviceAccount:${module.bulk_connector[each.key].instance_sa_email}"
  role      = "roles/secretmanager.secretAccessor"
}


# END LOOKUP TABLES

locals {
  api_instances = { for instance in module.api_connector :
    instance.instance_id => merge(
      {
        endpoint_url : instance.cloud_function_url,
        sanitized_bucket : try(instance.async_output_bucket_name, null),
      },
      instance,
      var.api_connectors[instance.instance_id]
    )
  }

  bulk_instances = { for instance in module.bulk_connector :
    instance.instance_id => merge(
      {
        sanitized_bucket_name : instance.sanitized_bucket
      },
      instance,
      var.bulk_connectors[instance.instance_id]
    )
  }

  webhook_collector_instances = { for k, instance in module.webhook_collector :
    k => merge(
      instance,
      var.webhook_collectors[k]
    )
  }

  all_instances = merge(local.api_instances, local.bulk_instances, local.webhook_collector_instances)
}

# script to test ALL connectors
resource "local_file" "test_all_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-all.sh"
  file_permission = "0770"
  content         = <<EOF
#!/bin/bash

echo "Testing API Connectors ..."

%{for test_script in values(module.api_connector)[*].test_script~}
./${test_script}
%{endfor}

echo "Testing Bulk Connectors ..."

%{for test_script in values(module.bulk_connector)[*].test_script~}
./${test_script}
%{endfor}
EOF
}

output "secrets_to_provision" {
  value = local.secrets_writable_by_instance
}
