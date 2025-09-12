
locals {

  # various values that are constants / sensible to expose as variables in the future

  # exec timeout for function, as set in infra level. GCP runtime should *kill* function execution if it exceeds this timeout
  function_exec_timeout_seconds = 300 # 5 minutes, at least avoids cron running MULTIPLE batches concurrenct, if that frequence is ALSO 5 minutes

  # timeout for batch processing, in seconds; must be LESS than function_exec_timeout, otherwise function might get killed by the runtime
  batch_invocation_timeout_seconds = 240 # 4 minutes

  # number of webhooks to process in a batch; dictates MAX messages pulled from pubsub in one-shot, as well as MAX webhooks written to GCS object
  batch_size = 100

  # would hope this is plenty, but could make configurable
  # if we assume each inbound webhook takes 200ms to parse, sanitized, publish to Pub-Sub, then
  # we can doe 5 req/s per instance, with concurrency == 1
  # 5 instances, gives us 25 req/s ... OK place to start
  max_instance_count = 5
}
# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances

data "google_project" "project" {
  project_id = var.project_id
}

# perms for secrets
# TODO: imho, would be cleaner to combine into a custom project role??
resource "google_secret_manager_secret_iam_member" "grant_sa_viewer_on_secret" {
  for_each = var.secret_bindings

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.viewer"
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = var.secret_bindings

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor" # this is ONLY accessing payload of a secret version
}


locals {

  side_outputs = { for k, v in {
    sanitized = var.side_output_sanitized
    original  = var.side_output_original
  } : k => v if v != null }

  side_outputs_to_provision    = { for k, v in local.side_outputs : k => v if v.bucket == null }
  side_outputs_to_grant_access = { for k, v in local.side_outputs : k => v if v.bucket != null }

  path_to_instance_config_parameters = "${coalesce(var.config_parameter_prefix, "")}${replace(upper(var.instance_id), "-", "_")}_"

}

# BEGIN AUTH KEYS
locals {
  allow_test_principals_to_sign = length(var.gcp_principals_authorized_to_test) > 0 && var.provision_auth_key != null
}


resource "google_kms_crypto_key" "webhook_auth_key" {
  count = var.provision_auth_key == null ? 0 : 1

  key_ring = var.key_ring_id
  name     = "${var.environment_id_prefix}${var.instance_id}-webhook-auth-key"
  purpose  = "ASYMMETRIC_SIGN"

  # just like aws, gcp will not auto-rotate ASYMMETRIC_SIGN keys; you will need to rotate them OUTSIDE of terraform, at your desired cadence

  version_template {
    algorithm = var.provision_auth_key.key_spec
  }

  labels = var.default_labels
}

locals {
  accepted_auth_keys = concat(
    var.webhook_auth_public_keys,
    var.provision_auth_key == null ? [] : [for key_id in google_kms_crypto_key.webhook_auth_key[*].id : "gcp-kms:${key_id}"]
  )

  # these ARE sorted, bc maps in tf are always iterated in lexicographic order of the keys
  auth_key_ids_sorted = values({
    for k in google_kms_crypto_key.webhook_auth_key[*] : try(k.labels.rotation_time, k.id) => k.id
  })
}


resource "google_kms_crypto_key_iam_member" "allow_test_principals_to_sign" {
  for_each = local.allow_test_principals_to_sign ? toset(var.gcp_principals_authorized_to_test) : toset([])

  crypto_key_id = google_kms_crypto_key.webhook_auth_key[0].id
  role          = "roles/cloudkms.signer"
  member        = each.key
}


# the webhook collector function needs to 1) be able to verify signatures, and 2) expose public key via JWKS endpoint (potentially???)
resource "google_kms_crypto_key_iam_member" "allow_function_to_access_public_key" {
  count = var.provision_auth_key == null ? 0 : 1

  crypto_key_id = google_kms_crypto_key.webhook_auth_key[0].id
  role          = var.oidc_token_verifier_role_id
  member        = "serviceAccount:${var.service_account_email}"
}

# END AUTH KEYS

module "rules_parameter" {
  source = "../gcp-sm-rules"

  project_id        = var.project_id
  instance_sa_email = var.service_account_email
  file_path         = var.rules_file
  prefix            = local.path_to_instance_config_parameters
}


resource "random_string" "bucket_name_random_sequence" {
  length  = 8
  special = false
  upper   = false
  lower   = true
  numeric = true

  lifecycle {
    # just NEVER recreate this random string; never what we're going to want to do, as will re-create the buckets
    ignore_changes = [
      length,
      special,
      lower,
      upper,
      numeric,
    ]
  }
}

# bucket to which sanitized webhooks will be written
module "sanitized_webhook_output" {
  source = "../gcp-output-bucket"

  project_id                     = var.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = var.service_account_email
  bucket_name_prefix             = "${var.environment_id_prefix}${var.instance_id}-${random_string.bucket_name_random_sequence.result}-"
  bucket_name_suffix             = "sanitized"
  sanitizer_accessor_principals = concat(
    var.gcp_principals_authorized_to_test,
    [for email in var.invoker_sa_emails : "serviceAccount:${email}"]
  )
}


module "side_output_bucket" {
  source = "../../modules/gcp-output-bucket"

  for_each = local.side_outputs_to_provision

  project_id                     = var.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = var.service_account_email
  bucket_name_prefix             = "${var.environment_id_prefix}${var.instance_id}-${random_string.bucket_name_random_sequence.result}-"
  bucket_name_suffix             = "side-output"
  sanitizer_accessor_principals  = each.value.allowed_readers
}

# TODO: will this work cross-project ?? concern would be that `bucket_write_role_id` is likely a project-level role
resource "google_storage_bucket_iam_member" "grant_sa_accessor_on_side_output_buckets" {
  for_each = local.side_outputs_to_grant_access

  bucket = replace(each.value.bucket, "gs://", "")
  member = "serviceAccount:${var.service_account_email}"
  role   = var.bucket_write_role_id
}

locals {
  side_output_env_vars = { for k, v in local.side_outputs :
    "SIDE_OUTPUT_${upper(k)}" => try(v.bucket, "gs://${module.side_output_bucket[k].bucket_name}")
  }

  # from v0.5, these will be required; for now, allow `null` but filter out so taken from config yaml
  # these are 'standard' env vars, expected from most connectors
  # any 'non-standard' ones can just be passed through var.environment_variables
  required_env_vars = { for k, v in {
    SOURCE             = var.source_kind
    ACCEPTED_AUTH_KEYS = join(",", local.accepted_auth_keys)
    ALLOW_ORIGINS      = "*" # TODO make configurable
    # AUTH_ISSUER                  = ""  # TODO: URL to collector itself, to be used to produce OpenID Connect Discovery Document
    REQUIRE_AUTHORIZATION_HEADER     = "true"
    WEBHOOK_OUTPUT                   = "https://pubsub.googleapis.com/${google_pubsub_topic.webhook_topic.id}"
    BATCH_MERGE_SUBSCRIPTION         = google_pubsub_subscription.webhook_subscription.id
    BATCH_SIZE                       = local.batch_size
    BATCH_INVOCATION_TIMEOUT_SECONDS = local.batch_invocation_timeout_seconds
    WEBHOOK_BATCH_OUTPUT             = "gs://${module.sanitized_webhook_output.bucket_name}"
    }
    : k => v if v != null
  }
}

# TODO: in 0.6, make this a 'google_parameter_manager_parameter' (requires google provide 6.25+)
# bc AUTH_ISSUER is the url of function, and not known at deploy-time, we cannot fill it in ENV VARS
# similarly, bc version number is not known at deploy-time, we cannot bind it via secret env vars
module "auth_issuer_secret" {
  source = "../../modules/gcp-secrets"

  secret_project    = var.project_id
  path_prefix       = local.path_to_instance_config_parameters
  replica_locations = var.secret_replica_locations
  secrets = {
    AUTH_ISSUER = {
      value       = google_cloudfunctions2_function.function.service_config[0].uri
      description = "Expected issuer of identity tokens for this collector"
    },
    SERVICE_URL = {
      value       = google_cloudfunctions2_function.function.service_config[0].uri
      description = "URL of the function as a web service"
    },
  }
  default_labels = var.default_labels
}

# grant access to secrets known AFTER function is deployed
# (eg, AUTH_ISSUER)
# distinct from var.secret_bindings; bc those are bound into the function's ENV VARS at deploy-time, grants must be done BEFORE deploy
locals {
  secrets_to_grant_access_to = {
    AUTH_ISSUER = {
      secret_id = module.auth_issuer_secret.secret_ids_within_project["AUTH_ISSUER"]
    }
  }
}

resource "google_secret_manager_secret_iam_member" "grant_sa_viewer_on_parameter" {
  for_each = local.secrets_to_grant_access_to

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.viewer"
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_parameter" {
  for_each = local.secrets_to_grant_access_to

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor" # this is ONLY accessing payload of a secret version
}


module "tf_runner" {
  source = "../../modules/gcp-tf-runner"
}

data "google_service_account" "function" {
  account_id = var.service_account_email
}


# to provision Cloud Function, TF must be able to act as the service account that the function will
# run as
resource "google_service_account_iam_member" "act_as" {
  member             = module.tf_runner.iam_principal
  role               = "roles/iam.serviceAccountUser"
  service_account_id = data.google_service_account.function.id
}

resource "google_cloudfunctions2_function" "function" {
  name        = "${var.environment_id_prefix}${var.instance_id}"
  description = "Webhook Collector - ${var.source_kind}"

  project  = var.project_id
  location = var.region

  build_config {
    runtime           = "java21"
    docker_repository = var.artifact_repository_id
    entry_point       = "co.worklytics.psoxy.GcpWebhookCollectorRoute"

    source {
      storage_source {
        bucket = var.artifacts_bucket_name
        object = var.deployment_bundle_object_name
      }
    }
  }

  service_config {
    service_account_email = var.service_account_email
    available_memory      = "${var.available_memory_mb}M"
    ingress_settings      = "ALLOW_ALL"
    timeout_seconds       = local.function_exec_timeout_seconds

    # TODO: setting this > 1 gives error: │ Error: Error updating function: googleapi: Error 400: Could not update Cloud Run service projects/psoxy-dev-erik/locations/us-central1/services/psoxy-dev-erik-llm-portal. spec.template.spec.containers.resources.limits.cpu: Invalid value specified for cpu. Total cpu < 1 is not supported with concurrency > 1.
    # max_instance_request_concurrency = 5 # q: make configurable? default is 1

    vpc_connector                 = var.vpc_config == null ? null : var.vpc_config.serverless_connector
    vpc_connector_egress_settings = var.vpc_config == null ? null : "ALL_TRAFFIC"

    max_instance_count = local.max_instance_count

    environment_variables = merge(
      local.required_env_vars,
      var.environment_variables,
      {
        PATH_TO_SHARED_CONFIG   = coalesce(var.config_parameter_prefix, ""),
        PATH_TO_INSTANCE_CONFIG = local.path_to_instance_config_parameters
      },
      local.side_output_env_vars,
    )

    dynamic "secret_environment_variables" {
      for_each = var.secret_bindings
      iterator = secret_environment_variable

      content {
        key        = secret_environment_variable.key
        project_id = data.google_project.project.number
        secret     = secret_environment_variable.value.secret_id
        version    = secret_environment_variable.value.version_number
      }
    }
  }

  labels = var.default_labels

  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret,
    google_service_account_iam_member.act_as
  ]
}

# ACL for webhook collection enforced inside the function
resource "google_cloud_run_service_iam_binding" "invokers" {
  project  = google_cloudfunctions2_function.function.project
  location = google_cloudfunctions2_function.function.location
  service  = google_cloudfunctions2_function.function.name

  role = "roles/run.invoker"

  # as long as this is 'allUsers', we don't need to grant any additional permissions to the invoker SA
  # (eg var.webhook_batch_invoker_sa_email)
  members = ["allUsers"]

}

# Pub/Sub topic for individual webhook messages
resource "google_pubsub_topic" "webhook_topic" {
  name    = "${var.environment_id_prefix}${var.instance_id}-webhooks"
  project = var.project_id

  labels = var.default_labels
}

# Pub/Sub subscription for batch processing
resource "google_pubsub_subscription" "webhook_subscription" {
  name    = "${var.environment_id_prefix}${var.instance_id}-webhook-subscription"
  topic   = google_pubsub_topic.webhook_topic.name
  project = var.project_id

  # Configure for batch processing
  ack_deadline_seconds       = 600       # 10 minutes to process messages
  message_retention_duration = "604800s" # 7 days retention

  # Enable batching for efficient processing
  enable_message_ordering = false

  # Configure expiration policy of the subscription; this is NOT about the messages themselves.
  expiration_policy {
    ttl = "" # No expiration
  }

  labels = var.default_labels
}

# IAM binding to allow the Cloud Function to publish to the topic
resource "google_pubsub_topic_iam_member" "publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.webhook_topic.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${var.service_account_email}"
}

# IAM binding to allow the Cloud Function to pull from the subscription
resource "google_pubsub_subscription_iam_member" "subscriber" {
  project      = var.project_id
  subscription = google_pubsub_subscription.webhook_subscription.name
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:${var.service_account_email}"
}


resource "google_cloud_scheduler_job" "trigger_batch_processing" {
  project     = var.project_id
  region      = var.region
  name        = "${var.environment_id_prefix}${var.instance_id}-batch-processing"
  schedule    = "*/${var.batch_processing_frequency_minutes} * * * *"
  time_zone   = "UTC"
  description = "trigger batch consumption of webhooks from Pub/Sub: ${google_pubsub_subscription.webhook_subscription.id}"

  http_target {
    http_method = "POST"
    uri         = google_cloudfunctions2_function.function.service_config[0].uri

    oidc_token {
      service_account_email = var.webhook_batch_invoker_sa_email
      audience              = google_cloudfunctions2_function.function.service_config[0].uri
    }

    # headers = {
    #   "Content-Type" = "application/json"
    # }

    # body = jsonencode({
    #   trigger = "scheduler"
    # })
  }
}


locals {
  proxy_endpoint_url  = google_cloudfunctions2_function.function.service_config[0].uri
  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  command_cli_call    = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js"

  command_test_logs = "node ${var.path_to_repo_root}tools/psoxy-test/cli-logs.js -p \"${google_cloudfunctions2_function.function.project}\" -f \"${google_cloudfunctions2_function.function.name}\""
}

locals {
  todo_content = <<EOT
## Testing ${google_cloudfunctions2_function.function.name}

Review the deployed Cloud function in GCP console:

[Function in GCP Console](https://console.cloud.google.com/functions/details/${google_cloudfunctions2_function.function.location}/${google_cloudfunctions2_function.function.name}?project=${google_cloudfunctions2_function.function.project})

We provide some Node.js scripts to easily validate the deployment. To be able
to run the test commands below, you need Node.js (>=16) and npm (v >=8)
installed. Then, ensure all dependencies are installed by running:

```shell
${local.command_npm_install}
```

### Make "test calls"
First, run an initial "Health Check" call to make sure the Psoxy instance is up and running:

```shell
${local.command_cli_call} -u ${local.proxy_endpoint_url} --health-check
```

Then, based on your configuration, these are some example test calls you can try (YMMV):

```shell
${local.command_cli_call} -u ${local.proxy_endpoint_url} --body '${coalesce(var.example_payload, "{\"test\": \"body\"}")}' --identity '${var.example_identity}'
```

Feel free to try the above calls, and reference to the source's API docs for other parameters /
endpoints to experiment with. If you spot any additional fields you believe should be
redacted/pseudonymized, feel free to modify [customize the rules](${var.path_to_repo_root}docs/gcp/custom-rules.md).

### Check logs (GCP runtime logs)

Based on your configuration, the following command allows you to inspect the logs of your Psoxy
deployment:

```shell
${local.command_test_logs}
```

---
Please, check the documentation of our [Psoxy Testing tools](${var.path_to_repo_root}tools/psoxy-test/README.md)
for a detailed description of all the different options.

Contact support@worklytics.co for assistance modifying the rules as needed.

EOT
}

resource "local_file" "test_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-${trimprefix(var.instance_id, var.environment_id_prefix)}.sh"
  file_permission = "755"
  content = templatefile("${path.module}/test_script.tftpl", {
    collector_endpoint_url = local.proxy_endpoint_url,
    function_name          = var.instance_id,
    command_cli_call       = local.command_cli_call,
    signing_key_id         = var.provision_auth_key == null ? null : google_kms_crypto_key.webhook_auth_key[0].id,
    example_payload        = coalesce(var.example_payload, "{\"test\": \"data\"}")
    example_identity       = var.example_identity
    collection_path        = "/"
  })
}

resource "local_file" "review" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${var.todo_step} - test ${google_cloudfunctions2_function.function.name}.md"
  content  = local.todo_content
}

output "instance_id" {
  value = var.instance_id
}

output "service_account_email" {
  value = google_cloudfunctions2_function.function.service_config[0].service_account_email
}

output "cloud_function_name" {
  value = google_cloudfunctions2_function.function.name
}

output "cloud_function_url" {
  value = local.proxy_endpoint_url
}

output "proxy_kind" {
  value       = "webhook-collector"
  description = "The kind of proxy instance this is."
}

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "output_sanitized_bucket_id" {
  value       = module.sanitized_webhook_output.bucket_name
  description = "Bucket ID (name) of the sanitized webhook output bucket."
}

output "side_output_sanitized_bucket_id" {
  value       = try(module.side_output_bucket["sanitized"].bucket_name, null)
  description = "Bucket ID (name) of the sanitized side output bucket, if any. May have been provided by the user, or provisioned by this module."
}

output "side_output_original_bucket_id" {
  value       = try(module.side_output_bucket["original"].bucket_name, null)
  description = "Bucket ID (name) of the original side output bucket, if any. May have been provided by the user, or provisioned by this module."
}


output "provisioned_auth_key_pairs" {
  value       = local.auth_key_ids_sorted
  description = "List of IDs of kms keys provisioned for webhook authentication purposes, if any."
}

output "todo" {
  value = local.todo_content
}
