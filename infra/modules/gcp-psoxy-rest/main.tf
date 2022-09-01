# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances

terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }
}

locals {
  secret_bindings = merge({
    PSOXY_SALT = {
      secret_id    = var.salt_secret_id
      version_number = var.salt_secret_version_number
    }
  }, var.secret_bindings)
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = local.secret_bindings

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
}

resource "google_cloudfunctions_function" "function" {
  name        = var.instance_id
  description = "Psoxy Connector - ${var.source_kind}"
  runtime     = "java11"
  project     = var.project_id
  region      = var.region

  available_memory_mb   = 1024
  source_archive_bucket = var.artifacts_bucket_name
  source_archive_object = var.deployment_bundle_object_name
  entry_point           = "co.worklytics.psoxy.Route"
  service_account_email = var.service_account_email

  environment_variables = merge(
    var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
    var.environment_variables
  )

  dynamic "secret_environment_variables" {
    for_each = local.secret_bindings
    iterator = secret_environment_variable

    content {
      key        = secret_environment_variable.key
      project_id = var.project_id
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
  test_commands = [for path in var.example_api_calls :
    "${var.path_to_repo_root}tools/test-psoxy.sh -g -u \"${local.proxy_endpoint_url}${path}\"${local.impersonation_param}"
  ]
}


resource "local_file" "review" {
  filename = "test ${google_cloudfunctions_function.function.name}.md"
  content  = <<EOT
Review the deployed Cloud function in GCP console:

https://console.cloud.google.com/functions/details/${var.region}/${google_cloudfunctions_function.function.name}?project=${var.project_id}

## Testing

From root of your checkout of the Psoxy repo, these are some example test calls you can try (YMMV):

```shell
${coalesce(join("\n", local.test_commands), "cd docs/example-api-calls/")}
```

See `docs/example-api-calls/` for more example API calls specific to the data source to which your
Proxy is configured to connect.

EOT
}


output "cloud_function_url" {
  value = local.proxy_endpoint_url
}
