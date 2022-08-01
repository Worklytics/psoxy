# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances

terraform {
  required_providers {
    google = {
      version = "~> 4.12"
    }
  }
}


resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = var.secret_bindings

  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = each.value.secret_name
}

resource "local_file" "review" {
  filename = "review ${google_cloudfunctions_function.function.name}.md"
  content  = <<EOT
Review the deployed Cloud function in GCP console:

https://console.cloud.google.com/functions/details/${var.region}/${google_cloudfunctions_function.function.name}?project=${var.project_id}

## Testing

If you want to test from your local machine:
```shell
export PSOXY_GCP_PROJECT=${var.project_id}
export PSOXY_GCP_REGION=${var.region}
export PSOXY_HOST=${var.region}-${var.project_id}
```

NOTE: if you want to customize the rule set used by Psoxy for your source, you can add a
`rules.yaml` file into the deployment directory (`target/deployment`) before invoking the command
above. The rules you define in the YAML file will override the ruleset specified in the codebase for
the source.

EOT
}

locals {
  secret_bindings = merge({
    PSOXY_SALT = {
      secret_name    = var.salt_secret_id
      secret_version = "latest"
    }
  }, var.secret_bindings)

}


resource "google_cloudfunctions_function" "function" {
  name        = "psoxy-${var.instance_id}"
  description = "Psoxy for ${var.instance_id} files"
  runtime     = "java11"
  project     = var.project_id
  region      = var.region

  available_memory_mb   = 1024
  source_archive_bucket = var.artifacts_bucket_name
  source_archive_object = var.deployment_bundle_object_name
  entry_point           = "co.worklytics.psoxy.Route"
  service_account_email = var.service_account_email

  environment_variables = tomap(yamldecode(file(var.path_to_config)))

  dynamic "secret_environment_variables" {
    for_each = local.secret_bindings

    content {
      key        = secret_environment_variables.key
      project_id = var.project_id
      secret     = secret_environment_variables.value.secret_name
      version    = secret_environment_variables.value.secret_version
    }
  }

  trigger_http = true
}

output "cloud_function_url" {
  value = "https://${var.region}-${var.project_id}.cloudfunctions.net/${google_cloudfunctions_function.function.name}"
}
