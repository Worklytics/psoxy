# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances

terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }
}

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
  # from v0.5, these will be required; for now, allow `null` but filter out so taken from config yaml
  # these are 'standard' env vars, expected from most connectors
  # any 'non-standard' ones can just be passed through var.environment_variables
  required_env_vars = { for k, v in {
    SOURCE                          = var.source_kind
    TARGET_HOST                     = var.target_host
    SOURCE_AUTH_STRATEGY_IDENTIFIER = var.source_auth_strategy
    OAUTH_SCOPES                    = join(" ", var.oauth_scopes)
    IDENTIFIER_SCOPE_ID             = var.identifier_scope_id
    }
    : k => v if v != null
  }
}

resource "google_cloudfunctions_function" "function" {
  name        = var.instance_id
  description = "Psoxy Connector - ${var.source_kind}"
  runtime     = "java11"
  project     = var.project_id
  region      = var.region

  available_memory_mb   = var.available_memory_mb
  source_archive_bucket = var.artifacts_bucket_name
  source_archive_object = var.deployment_bundle_object_name
  entry_point           = "co.worklytics.psoxy.Route"
  service_account_email = var.service_account_email

  environment_variables = merge(
    local.required_env_vars,
    var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
    var.environment_variables,
    var.config_parameter_prefix == null ? {} : { PATH_TO_SHARED_CONFIG = var.config_parameter_prefix },
    var.config_parameter_prefix == null ? {} : { PATH_TO_INSTANCE_CONFIG = "${var.config_parameter_prefix}${var.source_kind}" },
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

  trigger_http = true

  lifecycle {
    ignore_changes = [
      labels
    ]
  }

  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret
  ]
}

locals {
  proxy_endpoint_url  = "https://${var.region}-${var.project_id}.cloudfunctions.net/${google_cloudfunctions_function.function.name}"
  impersonation_param = var.example_api_calls_user_to_impersonate == null ? "" : " -i \"${var.example_api_calls_user_to_impersonate}\""
  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  command_cli_call    = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js"
  command_test_calls = [for path in var.example_api_calls :
    "${local.command_cli_call} -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]
  command_test_logs = "node ${var.path_to_repo_root}tools/psoxy-test/cli-logs.js -p \"${var.project_id}\" -f \"${google_cloudfunctions_function.function.name}\""
}

locals {
  todo_content = <<EOT
## Testing ${google_cloudfunctions_function.function.name}

Review the deployed Cloud function in GCP console:

[Function in GCP Console](https://console.cloud.google.com/functions/details/${var.region}/${google_cloudfunctions_function.function.name}?project=${var.project_id})

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
${coalesce(join("\n", local.command_test_calls), "cd docs/example-api-calls/")}
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
  filename        = "test-${var.instance_id}.sh"
  file_permission = "0770"
  content         = <<EOT
#!/bin/bash
API_PATH=$${1:-${try(var.example_api_calls[0], "")}}
echo "Quick test of ${var.instance_id} ..."

${local.command_cli_call} -u "${local.proxy_endpoint_url}" --health-check

${local.command_cli_call} -u "${local.proxy_endpoint_url}$API_PATH" ${local.impersonation_param}

echo "Invoke this script with any of the following as arguments to test other endpoints:${"\r\n\t"}${join("\r\n\t", var.example_api_calls)}"
EOT
}


resource "local_file" "review" {
  filename = "TODO ${var.todo_step} - test ${google_cloudfunctions_function.function.name}.md"
  content  = local.todo_content
}

output "instance_id" {
  value = var.instance_id
}

output "cloud_function_name" {
  value = google_cloudfunctions_function.function.name
}

output "cloud_function_url" {
  value = local.proxy_endpoint_url
}

output "todo" {
  value = local.todo_content
}

output "proxy_kind" {
  value       = "rest"
  description = "The kind of proxy instance this is."
}

output "next_todo_step" {
  value = var.todo_step + 1
}
