# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = var.secret_bindings

  project   = var.gcp_project.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
}

locals {
  side_outputs = { for k, v in {
    sanitized = var.side_output_sanitized
    original  = var.side_output_original
  } : k => v if v != null }

  side_outputs_to_provision    = { for k, v in local.side_outputs : k => v if v.bucket == null }
  side_outputs_to_grant_access = { for k, v in local.side_outputs : k => v if v.bucket != null }

  # whether ANY GCS buckets will need to be provisioned to support this instance
  bucket_provisioning_required = var.enable_async_processing || length(local.side_outputs_to_provision) > 0

  path_to_instance_config_parameters = "${coalesce(var.config_parameter_prefix, "")}${replace(upper(var.instance_id), "-", "_")}_"

  vpc_use_connector = var.vpc_config != null && try(var.vpc_config.serverless_connector, null) != null # TODO 0.7.x: remove
  vpc_use_direct    = var.vpc_config != null && !local.vpc_use_connector && try(var.vpc_config.network, null) != null && try(var.vpc_config.subnet, null) != null

  # gcp-output-bucket allows bucket_name_prefix up to 40 chars
  bucket_name_environment_id_prefix  = substr(var.environment_id_prefix, 0, 30)
  bucket_name_prefix_random_suffix   = local.bucket_provisioning_required ? "-${random_string.bucket_name_random_sequence[0].result}-" : ""
  bucket_name_instance_id_max_length = local.bucket_provisioning_required ? 40 - length(local.bucket_name_environment_id_prefix) - length(local.bucket_name_prefix_random_suffix) : 0
  bucket_name_instance_id            = local.bucket_provisioning_required ? trim(substr(var.instance_id, 0, max(0, local.bucket_name_instance_id_max_length)), "-") : ""
  bucket_name_prefix                 = local.bucket_provisioning_required ? "${local.bucket_name_environment_id_prefix}${local.bucket_name_instance_id}${local.bucket_name_prefix_random_suffix}" : null
}

resource "random_string" "bucket_name_random_sequence" {
  count = local.bucket_provisioning_required ? 1 : 0

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

module "async_output" {
  source = "../gcp-output-bucket"

  count = var.enable_async_processing ? 1 : 0

  project_id                     = var.gcp_project.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = var.service_account_email
  bucket_name_prefix             = local.bucket_name_prefix
  bucket_name_suffix             = "async-output"
  sanitizer_accessor_principals = concat(
    var.gcp_principals_authorized_to_test,
    [for email in var.invoker_sa_emails : "serviceAccount:${email}"]
  )
  enable_versioning              = var.enable_versioning
  bucket_access_logs_destination = var.bucket_access_logs_destination
  allowed_accessor_ip_blocks     = var.allowed_data_access_ip_blocks
}

# Pub/Sub topic for async output (if enabled)
resource "google_pubsub_topic" "async_output_topic" {
  count = var.enable_async_processing ? 1 : 0

  project = var.gcp_project.project_id
  name    = "${var.environment_id_prefix}${var.instance_id}-async-output"
}

# Pub/Sub push subscription for async output (if enabled)
resource "google_pubsub_subscription" "async_output_subscription" {
  count = var.enable_async_processing ? 1 : 0

  project = var.gcp_project.project_id
  name    = "${var.environment_id_prefix}${var.instance_id}-async-output-subscription"
  topic   = google_pubsub_topic.async_output_topic[0].name

  # Push config: deliver messages to the Cloud Function's HTTP endpoint (not an external ALB URL)
  push_config {
    push_endpoint = local.cloud_function_url

    oidc_token {
      service_account_email = var.service_account_email
      audience              = local.cloud_function_url
    }
  }

  ack_deadline_seconds       = 600       # 10 minutes to process messages
  message_retention_duration = "604800s" # 7 days retention
  enable_message_ordering    = false

  # Configure expiration policy of the subscription; this is NOT about the messages themselves.
  expiration_policy {
    ttl = "" # No expiration
  }
}

# IAM permissions for Pub/Sub async processing

locals {
  # TODO: there's a `google_project_service_identity` resource in `google-beta` provider, which we might be able to leverage from 0.6+
  pubsub_service_identity = "service-${var.gcp_project.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}

# 1. Allow the Cloud Function's service account to publish to the topic
resource "google_pubsub_topic_iam_member" "function_publisher" {
  count = var.enable_async_processing ? 1 : 0

  project = var.gcp_project.project_id
  topic   = google_pubsub_topic.async_output_topic[0].id
  member  = "serviceAccount:${var.service_account_email}"
  role    = "roles/pubsub.publisher"
}

# 2. Allow the Pub/Sub service account sign messages as the function'sSA
# this is in ADDITION to allowing the function's SA to invoke the function itself, which is ABOVE
#
# NOTE: according to https://cloud.google.com/pubsub/docs/authenticate-push-subscriptions#configure_for_push_authentication
# we need to grant Project-level token creator role to PubSub service account - but seems NOT in practice (and per ChatGPT)
resource "google_service_account_iam_member" "pubsub_oidc_minter" {
  count = var.enable_async_processing ? 1 : 0

  service_account_id = "projects/${var.gcp_project.project_id}/serviceAccounts/${var.service_account_email}"
  member             = "serviceAccount:${local.pubsub_service_identity}"
  role               = "roles/iam.serviceAccountOpenIdTokenCreator"
}

module "side_output_bucket" {
  source = "../../modules/gcp-output-bucket"

  for_each = local.side_outputs_to_provision

  project_id                     = var.gcp_project.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = var.service_account_email
  bucket_name_prefix             = local.bucket_name_prefix
  bucket_name_suffix             = "side-output"
  sanitizer_accessor_principals  = each.value.allowed_readers
  enable_versioning              = var.enable_versioning
  bucket_access_logs_destination = var.bucket_access_logs_destination
  allowed_accessor_ip_blocks     = var.allowed_data_access_ip_blocks
}

# TODO: will this work cross-project ?? concern would be that `bucket_write_role_id` is likely a project-level role
resource "google_storage_bucket_iam_member" "grant_sa_accessor_on_side_output_buckets" {
  for_each = local.side_outputs_to_grant_access

  bucket = replace(each.value.bucket, "gs://", "")
  member = "serviceAccount:${var.service_account_email}"
  role   = var.bucket_write_role_id
}

# Grant read access to the remote resource bucket (for rules, NLP models, etc.)
resource "google_storage_bucket_iam_member" "grant_sa_reader_on_remote_resource_bucket" {
  count = var.remote_resource_bucket != null ? 1 : 0

  bucket = var.remote_resource_bucket
  member = "serviceAccount:${var.service_account_email}"
  role   = "roles/storage.objectViewer"

  dynamic "condition" {
    for_each = (var.remote_resource_instance_path != null || var.remote_resource_shared_path != null) ? [1] : []
    content {
      title = "scope_to_resource_paths"
      expression = join(" || ", compact([
        var.remote_resource_instance_path != null ? "resource.name.startsWith(\"projects/_/buckets/${var.remote_resource_bucket}/objects/${var.remote_resource_instance_path}\")" : null,
        var.remote_resource_shared_path != null ? "resource.name.startsWith(\"projects/_/buckets/${var.remote_resource_bucket}/objects/${var.remote_resource_shared_path}\")" : null,
      ]))
    }
  }
}


locals {
  side_output_env_vars = { for k, v in local.side_outputs :
    "SIDE_OUTPUT_${upper(k)}" => try(v.bucket, "gs://${module.side_output_bucket[k].bucket_name}")
  }

  # from v0.5, these will be required; for now, allow `null` but filter out so taken from config yaml
  # these are 'standard' env vars, expected from most connectors
  # any 'non-standard' ones can just be passed through var.environment_variables
  required_env_vars = { for k, v in {
    SOURCE                          = var.source_kind
    TARGET_HOST                     = var.target_host
    SOURCE_AUTH_STRATEGY_IDENTIFIER = var.source_auth_strategy
    OAUTH_SCOPES                    = join(" ", var.oauth_scopes)
    ASYNC_PUB_SUB_QUEUE             = var.enable_async_processing ? google_pubsub_topic.async_output_topic[0].id : null
    }
    : k => v if v != null
  }
}

# to provision Cloud Function, TF must be able to act as the service account that the function will
# run as
# NOTE: named 'tf_runner_act_as' rather than 'act_as' to avoid replacement-cycle on upgrades where
# tf_runner_iam_principal changes (eg 0.5.x -> 0.6.x); separate create+destroy is cycle-free.
resource "google_service_account_iam_member" "tf_runner_act_as" {
  member             = var.tf_runner_iam_principal
  role               = "roles/iam.serviceAccountUser"
  service_account_id = "projects/${var.gcp_project.project_id}/serviceAccounts/${var.service_account_email}"
}

# migration: remove old resource address from state (destroyed in GCP)
removed {
  from = google_service_account_iam_member.act_as
  lifecycle {
    destroy = true
  }
}


resource "google_cloudfunctions2_function" "function" {
  name        = "${var.environment_id_prefix}${var.instance_id}"
  description = "Psoxy Connector - ${var.source_kind}"

  project  = var.gcp_project.project_id
  location = var.region

  build_config {
    runtime           = "java25"
    docker_repository = var.artifact_repository_id
    entry_point       = "co.worklytics.psoxy.Route"
    service_account   = var.builder_sa_id

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
    timeout_seconds       = var.timeout_seconds
    ingress_settings      = var.ingress_settings

    max_instance_request_concurrency = var.instance_concurrency
    available_cpu                    = var.instance_concurrency > 1 ? "1" : null
    max_instance_count               = var.max_instance_count

    vpc_connector                 = local.vpc_use_connector ? var.vpc_config.serverless_connector : null
    vpc_connector_egress_settings = local.vpc_use_connector ? "ALL_TRAFFIC" : null

    direct_vpc_egress = local.vpc_use_direct ? "VPC_EGRESS_ALL_TRAFFIC" : null

    dynamic "direct_vpc_network_interface" {
      for_each = local.vpc_use_direct ? [var.vpc_config] : []
      content {
        network    = direct_vpc_network_interface.value.network
        subnetwork = direct_vpc_network_interface.value.subnet
      }
    }

    environment_variables = merge(
      # { LOG_EXECUTION_ID = "true" }, # NOTE that the google provider > 5.x seems to magically add this here, seemingly bc that's the defalt behavior of the version gcloud cli / API its using
      local.required_env_vars,
      {
        REQUEST_TIMEOUT_SECONDS = tostring(var.timeout_seconds)
      },
      var.environment_variables,
      var.config_parameter_prefix == null ? {} : { PATH_TO_SHARED_CONFIG = var.config_parameter_prefix },
      var.config_parameter_prefix == null ? {} : { PATH_TO_INSTANCE_CONFIG = "${var.config_parameter_prefix}${replace(upper(var.instance_id), "-", "_")}_" },
      var.allowed_data_access_ip_blocks != null ? { ALLOWED_DATA_ACCESS_IP_BLOCKS = join(",", var.allowed_data_access_ip_blocks) } : {},
      local.side_output_env_vars,
      var.enable_async_processing ? { ASYNC_OUTPUT_DESTINATION = "gs://${module.async_output[0].bucket_name}" } : {},
      var.remote_resource_bucket != null ? { REMOTE_RESOURCE_BUCKET = var.remote_resource_bucket } : {},
      var.remote_resource_instance_path != null ? { INSTANCE_RESOURCE_PATH = var.remote_resource_instance_path } : {},
      var.remote_resource_shared_path != null ? { SHARED_RESOURCE_PATH = var.remote_resource_shared_path } : {},
    )

    dynamic "secret_environment_variables" {
      for_each = var.secret_bindings
      iterator = secret_environment_variable

      content {
        key = secret_environment_variable.key
        # project_id string (not number) avoids apply-time drift: number comes from data.google_project
        project_id = var.gcp_project.project_id
        secret     = secret_environment_variable.value.secret_id
        version    = secret_environment_variable.value.version_number
      }
    }
  }

  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret,
    google_service_account_iam_member.tf_runner_act_as,
  ]
}

# TODO: in 0.6, make this a 'google_parameter_manager_parameter' (requires google provide 6.25+)
# bc SERVICE_URL is the url of function, and not known at deploy-time, we cannot fill it in ENV VARS
# similarly, bc version number is not known at deploy-time, we cannot bind it via secret env vars
module "service_url_parameter" {
  source = "../../modules/gcp-secrets"

  count = var.enable_async_processing ? 1 : 0

  secret_project    = var.gcp_project.project_id
  path_prefix       = local.path_to_instance_config_parameters
  replica_locations = var.secret_replica_locations
  secrets = {
    SERVICE_URL = {
      value       = google_cloudfunctions2_function.function.service_config[0].uri
      description = "URL of the function as a web service"
    },
  }
}


# grant access to secrets known AFTER function is deployed
# (eg, SERVICE_URL)
# distinct from var.secret_bindings; bc those are bound into the function's ENV VARS at deploy-time, grants must be done BEFORE deploy
locals {
  secrets_to_grant_access_to = try({
    SERVICE_URL = {
      secret_id = module.service_url_parameter[0].secret_ids_within_project["SERVICE_URL"]
    }
  }, {})
}

resource "google_secret_manager_secret_iam_member" "grant_sa_viewer_on_parameter" {
  for_each = local.secrets_to_grant_access_to

  project   = var.gcp_project.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.viewer"
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_parameter" {
  for_each = local.secrets_to_grant_access_to

  project   = var.gcp_project.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor" # this is ONLY accessing payload of a secret version
}


# bizarrely, `google_cloudfunctions2_function_iam_binding` doesn't work for this; wtf?
resource "google_cloud_run_service_iam_binding" "invokers" {
  project  = google_cloudfunctions2_function.function.project
  location = google_cloudfunctions2_function.function.location
  service  = google_cloudfunctions2_function.function.name

  role = "roles/run.invoker"

  members = concat(
    # actually expected callers
    [for email in var.invoker_sa_emails : "serviceAccount:${email}"],
    # testers, if any
    var.gcp_principals_authorized_to_test,
    # itself, if async processing is enabled
    var.enable_async_processing ? ["serviceAccount:${var.service_account_email}"] : [],
  )

  # Cloud Run IAM conditions only support request.host and request.path — not source IP.
  # IP allowlisting for API data access is enforced in the proxy (ALLOWED_DATA_ACCESS_IP_BLOCKS).
}

locals {
  cloud_function_url = google_cloudfunctions2_function.function.service_config[0].uri
  # Public URL for tests/TODOs: per-connector path on shared external LB, else Cloud Function URI
  proxy_endpoint_url = trimsuffix(
    var.external_lb_base_url != null
    ? "${trimsuffix(var.external_lb_base_url, "/")}/${google_cloudfunctions2_function.function.name}"
    : local.cloud_function_url,
    "/"
  )
  impersonation_param = var.example_api_calls_user_to_impersonate == null ? "" : " -i \"${var.example_api_calls_user_to_impersonate}\""
  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  # -f gcp: non-*.run.app / non-*.cloudfunctions.net hosts must be flagged explicitly as a GCP-hosted deployment
  # --allow-insecure-tls: IP hosts (self-signed PoC ALB) proceed despite untrusted cert
  proxy_endpoint_is_cloud_function = can(regex("\\.run\\.app", local.proxy_endpoint_url)) || can(regex("\\.cloudfunctions\\.net", local.proxy_endpoint_url))
  # Prefer split/trimprefix over regex capture groups (capture groups return a list when >1 group)
  proxy_endpoint_host = split("/", trimprefix(trimprefix(local.proxy_endpoint_url, "https://"), "http://"))[0]
  proxy_endpoint_is_ip = can(regex("^[0-9.]+$", local.proxy_endpoint_host)) || can(regex("^\\[[0-9a-fA-F:]+\\]$", local.proxy_endpoint_host))
  command_cli_call_flags = trimspace(join(" ", compact([
    local.proxy_endpoint_is_cloud_function ? "" : "-f gcp",
    local.proxy_endpoint_is_ip ? "--allow-insecure-tls" : "",
  ])))
  command_cli_call = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js${local.command_cli_call_flags != "" ? " ${local.command_cli_call_flags}" : ""}"

  # Merge example_api_calls into example_api_requests for unified processing
  all_example_api_requests = concat(
    [for path in var.example_api_calls : {
      method       = "GET"
      path         = path
      content_type = "application/json"
      body         = null
    }],
    var.example_api_requests
  )

  example_api_get_requests = [for r in local.all_example_api_requests : r if r.method == "GET"]

  example_request_header_flags = { for request in local.all_example_api_requests :
    "${request.method} ${request.path}" => join("", [for name, value in try(request.headers, {}) : " -H \"${name}: ${value}\""])
  }

  # Shell script positional args; omit empty content-type/body/header slots unless needed
  example_api_script_invocation = { for request in local.all_example_api_requests :
    "${request.method} ${request.path}" => join(" ", concat(
      [request.method, "'${replace(request.path, "'", "'\\''")}'"],
      request.body != null ? [
        coalesce(request.content_type, "application/json"),
        "'${replace(request.body, "\"", "\\\"")}'"
      ] : [],
      (request.body == null && trimspace(lookup(local.example_request_header_flags, "${request.method} ${request.path}", "")) != "") ? ["''", "''"] : [],
      trimspace(lookup(local.example_request_header_flags, "${request.method} ${request.path}", "")) != "" ? [
        "'${replace(trimspace(lookup(local.example_request_header_flags, "${request.method} ${request.path}", "")), "'", "'\\''")}'"
      ] : []
    ))
  }

  example_api_get_requests_for_script = [for r in local.example_api_get_requests : merge(r, {
    header_flags      = trimspace(lookup(local.example_request_header_flags, "${r.method} ${r.path}", ""))
    script_invocation = local.example_api_script_invocation["${r.method} ${r.path}"]
  })]

  example_api_post_requests_for_script = [for r in local.all_example_api_requests : merge(r, {
    header_flags      = trimspace(lookup(local.example_request_header_flags, "${r.method} ${r.path}", ""))
    script_invocation = local.example_api_script_invocation["${r.method} ${r.path}"]
  }) if r.method == "POST" && r.body != null]

  example_api_script_invocations = concat(
    [for r in local.example_api_get_requests_for_script : r.script_invocation],
    [for r in local.example_api_post_requests_for_script : r.script_invocation]
  )

  test_script_filename = "test-${trimprefix(var.instance_id, var.environment_id_prefix)}.sh"
  default_header_flags = length(local.example_api_get_requests_for_script) > 0 ? local.example_api_get_requests_for_script[0].header_flags : ""

  # Generate test calls from all example requests
  command_test_calls = [for request in local.all_example_api_requests :
    "${local.command_cli_call} -u \"${local.proxy_endpoint_url}${request.path}\" -m ${request.method}${request.body != null ? " -b \"${request.body}\"" : ""}${local.example_request_header_flags["${request.method} ${request.path}"]}${local.impersonation_param}"
  ]
  command_test_logs = "node ${var.path_to_repo_root}tools/psoxy-test/cli-logs.js -p \"${google_cloudfunctions2_function.function.project}\" -f \"${google_cloudfunctions2_function.function.name}\""
}

locals {
  todo_content = <<EOT
## Testing ${google_cloudfunctions2_function.function.name}

Review the deployed Cloud function in GCP console:

[Function in GCP Console](https://console.cloud.google.com/functions/details/${google_cloudfunctions2_function.function.location}/${google_cloudfunctions2_function.function.name}?project=${google_cloudfunctions2_function.function.project})

We provide some Node.js scripts to easily validate the deployment. To be able
to run the test commands below, you need Node.js (>=20) and npm (v >=8)
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
${join("\n", local.command_test_calls)}
```

Feel free to try the above calls, and reference to the source's API docs for other parameters /
endpoints to experiment with. If you spot any additional fields you believe should be
redacted/pseudonymized, feel free to modify [customize the rules](${var.path_to_repo_root}docs/gcp/custom-rules.md).

### Or use the test script

We also generated `${local.test_script_filename}`, a wrapper script around the test tool.
Run it with no arguments to exercise the default example endpoint, or pass arguments to try
others:

```shell
./${local.test_script_filename}
```

```shell
${join("\n", [for invocation in local.example_api_script_invocations : "./${local.test_script_filename} ${invocation}"])}
```

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
    proxy_endpoint_url        = local.proxy_endpoint_url,
    function_name             = var.instance_id,
    impersonation_param       = local.impersonation_param,
    command_cli_call          = local.command_cli_call,
    default_header_flags      = local.default_header_flags,
    example_api_get_requests  = local.example_api_get_requests_for_script,
    example_api_post_requests = local.example_api_post_requests_for_script,
    enable_async_processing   = var.enable_async_processing
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
  value = local.cloud_function_url
}

output "proxy_endpoint_url" {
  description = "Public URL used for tests / TODOs (per-connector path on external_lb_base_url when set, otherwise Cloud Function URI)."
  value       = local.proxy_endpoint_url
}

output "proxy_kind" {
  value       = "rest"
  description = "The kind of proxy instance this is."
}

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "async_output_bucket_id" {
  value       = try(module.async_output[0].bucket_id, null)
  description = "Bucket ID of the async output bucket, if any. May have been provided by the user, or provisioned by this module."
}

output "side_output_sanitized_bucket_id" {
  value       = try(module.side_output_bucket["sanitized"].bucket_id, null)
  description = "Bucket ID of the sanitized side output bucket, if any. May have been provided by the user, or provisioned by this module."
}

output "side_output_original_bucket_id" {
  value       = try(module.side_output_bucket["original"].bucket_id, null)
  description = "Bucket ID of the original side output bucket, if any. May have been provided by the user, or provisioned by this module."
}

output "async_output_bucket_name" {
  value       = try(module.async_output[0].bucket_name, null)
  description = "Name of the async output bucket, if any. May have been provided by the user, or provisioned by this module."
}

output "todo" {
  value = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "function_config" {
  description = "INTERNAL USE ONLY - Cloud Function configuration for CI/testing purposes. Users should NOT rely on this output's presence, structure, or schema as it may change without notice."
  value       = google_cloudfunctions2_function.function
}
