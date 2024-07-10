terraform {
  required_providers {
    google = {
      version = ">= 3.74, <= 5.0"
    }
  }
}

# constants
locals {
  SA_NAME_MIN_LENGTH             = 6
  SA_NAME_MAX_LENGTH             = 30
  CLOUD_FUNCTION_NAME_MAX_LENGTH = 63
}

# computed
locals {
  # legacy pre 0.5 may not pass instance_id
  instance_id         = coalesce(var.instance_id, substr(var.source_kind, 0, local.CLOUD_FUNCTION_NAME_MAX_LENGTH - length(var.environment_id_prefix)))
  default_sa_name     = coalesce("${var.environment_id_prefix}${local.instance_id}", substr("${var.environment_id_prefix}fn-${var.source_kind}", 0, local.SA_NAME_MAX_LENGTH - 3 - length(var.environment_id_prefix)))
  sa_name             = length(local.default_sa_name) < local.SA_NAME_MIN_LENGTH ? "psoxy-${local.default_sa_name}" : local.default_sa_name
  function_name       = "${var.environment_id_prefix}${local.instance_id}"
  command_npm_install = "npm --prefix ${var.psoxy_base_dir}tools/psoxy-test install"
}

data "google_project" "project" {
  project_id = var.project_id
}

# ensure Storage API is activated
resource "google_project_service" "gcp_infra_api" {
  for_each = toset([
    "storage.googleapis.com",
  ])

  service                    = each.key
  project                    = var.project_id
  disable_dependent_services = false
  disable_on_destroy         = false
}



resource "random_string" "bucket_id_part" {
  length  = 8
  special = false
  lower   = true
  upper   = false
  numeric = true
}

locals {
  bucket_prefix = "${local.function_name}-${random_string.bucket_id_part.id}"
}

# data input to function
resource "google_storage_bucket" "input_bucket" {
  project                     = var.project_id
  name                        = coalesce(var.input_bucket_name, "${local.bucket_prefix}-input")
  location                    = var.region
  force_destroy               = true
  uniform_bucket_level_access = true
  labels                      = var.default_labels

  lifecycle_rule {
    condition {
      age = var.input_expiration_days
    }

    action {
      type = "Delete"
    }
  }

  lifecycle {
    ignore_changes = [
      labels
    ]
  }

  depends_on = [
    google_project_service.gcp_infra_api
  ]
}

# data output from function
module "output_bucket" {
  source = "../gcp-output-bucket"

  project_id                     = var.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = google_service_account.service_account.email
  bucket_name_prefix             = coalesce(var.sanitized_bucket_name, local.bucket_prefix)
  bucket_name_suffix             = var.sanitized_bucket_name == null ? "-output" : ""
  region                         = var.region
  expiration_days                = var.sanitized_expiration_days
  bucket_labels                  = var.default_labels

  depends_on = [
    google_project_service.gcp_infra_api
  ]
}

resource "google_service_account" "service_account" {
  project      = var.project_id
  account_id   = local.sa_name
  display_name = "Psoxy Connector - ${var.source_kind}"
  description  = "${local.function_name} runs as this service account"
}

resource "google_storage_bucket_iam_member" "access_for_import_bucket" {
  bucket = google_storage_bucket.input_bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.service_account.email}"
}

resource "google_storage_bucket_iam_member" "grant_sa_read_on_processed_bucket" {
  count = length(var.worklytics_sa_emails)

  bucket = module.output_bucket.bucket_name
  member = "serviceAccount:${var.worklytics_sa_emails[count.index]}"
  role   = "roles/storage.objectViewer"
}

resource "google_storage_bucket_iam_member" "grant_testers_admin_on_import_bucket" {
  for_each = toset(var.gcp_principals_authorized_to_test)

  bucket = google_storage_bucket.input_bucket.name
  role   = "roles/storage.objectAdmin"
  member = each.value
}

resource "google_storage_bucket_iam_member" "grant_testers_admin_on_processed_bucket" {
  for_each = toset(var.gcp_principals_authorized_to_test)

  bucket = module.output_bucket.bucket_name
  role   = "roles/storage.objectAdmin"
  member = each.value
}



resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = var.secret_bindings

  project   = var.project_id
  secret_id = each.value.secret_id
  member    = "serviceAccount:${google_service_account.service_account.email}"
  role      = "roles/secretmanager.secretAccessor"
}

module "tf_runner" {
  source = "../../modules/gcp-tf-runner"
}

# to provision Cloud Function, TF must be able to act as the service account that the function will
# run as
resource "google_service_account_iam_member" "act_as" {
  member             = module.tf_runner.iam_principal
  role               = "roles/iam.serviceAccountUser"
  service_account_id = google_service_account.service_account.id
}

resource "google_cloudfunctions_function" "function" {
  name                  = local.function_name
  description           = "Psoxy instance to process ${var.source_kind} files"
  runtime               = "java17"
  project               = var.project_id
  region                = var.region
  available_memory_mb   = coalesce(var.available_memory_mb, 1024)
  source_archive_bucket = var.artifacts_bucket_name
  source_archive_object = var.deployment_bundle_object_name
  docker_repository     = var.artifact_repository_id
  entry_point           = "co.worklytics.psoxy.GCSFileEvent"
  service_account_email = google_service_account.service_account.email
  timeout               = 540 # 9 minutes, which is gen1 max allowed
  labels                = var.default_labels
  docker_registry       = "CONTAINER_REGISTRY"

  environment_variables = merge(tomap({
    INPUT_BUCKET  = google_storage_bucket.input_bucket.name,
    OUTPUT_BUCKET = module.output_bucket.bucket_name,
    }),
    var.path_to_config == null ? {} : yamldecode(file(var.path_to_config)),
    var.environment_variables,
    var.config_parameter_prefix == null ? {} : { PATH_TO_SHARED_CONFIG = var.config_parameter_prefix },
    var.config_parameter_prefix == null ? {} : { PATH_TO_INSTANCE_CONFIG = "${var.config_parameter_prefix}${replace(upper(var.instance_id), "-", "_")}_" },
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

  event_trigger {
    event_type = "google.storage.object.finalize"
    resource   = google_storage_bucket.input_bucket.name
  }

  lifecycle {
    ignore_changes = [
      labels
    ]
  }

  # can't provision function until grants that allow reading of secrets, acting as SA are complete
  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret,
    google_service_account_iam_member.act_as
  ]
}

locals {
  example_file = var.example_file == null ? "/path/to/example/file.csv" : "${var.psoxy_base_dir}${var.example_file}"
  todo_brief   = <<EOT
## Test ${local.function_name}
Check that the Psoxy works as expected and it transforms the files of your input bucket following
the rules you have defined:

```shell
node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f ${local.example_file} -d GCP -i ${google_storage_bucket.input_bucket.name} -o ${module.output_bucket.bucket_name}
```
EOT
}

resource "local_file" "todo_test_gcp_psoxy_bulk" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${var.todo_step} - test ${local.function_name}.md"
  content  = <<EOT
# Testing Psoxy Bulk: ${local.function_name}

Review the deployed Cloud function in GCP console:

[Function in GCP Console](https://console.cloud.google.com/functions/details/${var.region}/${google_cloudfunctions_function.function.name}?project=${var.project_id})

We provide some Node.js scripts to easily validate the deployment. To be able
to run the test commands below, you need Node.js (>=16) and npm (v >=8)
installed. Ensure all dependencies are installed by running:

```shell
${local.command_npm_install}
```

${local.todo_brief}

Notice that the rest of the options should match your Psoxy configuration.

(*) Check supported formats in [Bulk Data Imports Docs](https://app.worklytics.co/docs/hris-import)

---

Please, check the documentation of our [Psoxy Testing tools](${var.psoxy_base_dir}tools/psoxy-test/README.md)
for a detailed description of all the different options.

EOT
}

resource "local_file" "test_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-${trimprefix(local.instance_id, var.environment_id_prefix)}.sh"
  file_permission = "755"
  content         = <<EOT
#!/bin/bash
FILE_PATH=$${1:-${try(local.example_file, "")}}
BLUE='\e[0;34m'
NC='\e[0m'

printf "Quick test of $${BLUE}${local.function_name}$${NC} ...\n"tf

node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f $FILE_PATH -d GCP -i ${google_storage_bucket.input_bucket.name} -o ${module.output_bucket.bucket_name}

if gzip -t "$FILE_PATH"; then
  printf "test file was compressed, so not testing compression as a separate case\n"
else
  printf "testing with compressed input file ... \n"
  # extract the file name from the path
  TEST_FILE_NAME=./$(basename $FILE_PATH)

  gzip -c $FILE_PATH > $TEST_FILE_NAME
  node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f $TEST_FILE_NAME -d GCP -i ${google_storage_bucket.input_bucket.name} -o ${module.output_bucket.bucket_name}
  rm $TEST_FILE_NAME
fi

EOT

}

output "instance_id" {
  value = local.instance_id
}

output "function_name" {
  value = local.function_name
}

output "instance_sa_email" {
  value = google_service_account.service_account.email
}

output "bucket_prefix" {
  value = local.bucket_prefix
}

output "input_bucket" {
  value = google_storage_bucket.input_bucket.name
}

output "sanitized_bucket" {
  value = module.output_bucket.bucket_name
}

output "proxy_kind" {
  value       = "bulk"
  description = "The kind of proxy instance this is."
}

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "todo" {
  value = local.todo_brief
}

output "next_todo_step" {
  value = var.todo_step + 1
}