# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances

data "google_project" "project" {
  project_id = var.project_id
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = var.secret_bindings

  project   = var.project_id
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
}

resource "random_string" "bucket_name_random_sequence" {
  count = length(local.side_outputs_to_provision) > 0 ? 1 : 0

  length  = 8
  special = false
  upper   = false
  lower   = true
  numeric = true
}

module "async_output" {
  source = "../gcp-output-bucket"

  count = var.enable_async_processing ? 1 : 0

  project_id                     = var.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = var.service_account_email
  bucket_name_prefix             = "${var.environment_id_prefix}${var.instance_id}-${random_string.bucket_name_random_sequence[0].result}-"
  bucket_name_suffix             = "async-output"
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
  bucket_name_prefix             = "${var.environment_id_prefix}${var.instance_id}-${random_string.bucket_name_random_sequence[0].result}-"
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
    SOURCE                          = var.source_kind
    TARGET_HOST                     = var.target_host
    SOURCE_AUTH_STRATEGY_IDENTIFIER = var.source_auth_strategy
    OAUTH_SCOPES                    = join(" ", var.oauth_scopes)
    }
    : k => v if v != null
  }
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
  description = "Psoxy Connector - ${var.source_kind}"

  project  = var.project_id
  location = var.region

  build_config {
    runtime           = "java21"
    docker_repository = var.artifact_repository_id
    entry_point       = "co.worklytics.psoxy.Route"

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

    environment_variables = merge(
      local.required_env_vars,
      var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
      var.environment_variables,
      var.config_parameter_prefix == null ? {} : { PATH_TO_SHARED_CONFIG = var.config_parameter_prefix },
      var.config_parameter_prefix == null ? {} : { PATH_TO_INSTANCE_CONFIG = "${var.config_parameter_prefix}${replace(upper(var.instance_id), "-", "_")}_" },
      local.side_output_env_vars,
      var.enable_async_processing ? { ASYNC_OUTPUT_DESTINATION = "gs://${module.async_output[0].bucket_name}" } : {},
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

  lifecycle {
    ignore_changes = [
      labels
    ]
  }

  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret,
    google_service_account_iam_member.act_as
  ]
}

# bizarrely, `google_cloudfunctions2_function_iam_binding` doesn't work for this; wtf?
resource "google_cloud_run_service_iam_binding" "invokers" {
  project  = google_cloudfunctions2_function.function.project
  location = google_cloudfunctions2_function.function.location
  service  = google_cloudfunctions2_function.function.name

  role = "roles/run.invoker"

  members = concat(
    [for email in var.invoker_sa_emails : "serviceAccount:${email}"],
    var.gcp_principals_authorized_to_test
  )
}

locals {
  proxy_endpoint_url  = google_cloudfunctions2_function.function.service_config[0].uri
  impersonation_param = var.example_api_calls_user_to_impersonate == null ? "" : " -i \"${var.example_api_calls_user_to_impersonate}\""
  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  command_cli_call    = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js"

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

  # Generate test calls from all example requests
  command_test_calls = [for request in local.all_example_api_requests :
    "${local.command_cli_call} -u \"${local.proxy_endpoint_url}${request.path}\" -m ${request.method}${request.body != null ? " -b \"${request.body}\"" : ""}${local.impersonation_param}"
  ]
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
${join("\n", local.command_test_calls)}
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
    proxy_endpoint_url      = local.proxy_endpoint_url,
    function_name           = var.instance_id,
    impersonation_param     = local.impersonation_param,
    command_cli_call        = local.command_cli_call,
    all_example_requests    = local.all_example_api_requests,
    enable_async_processing = var.enable_async_processing
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

output "todo" {
  value = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}
