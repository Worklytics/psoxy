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

resource "random_string" "bucket_id_part" {
  length  = 8
  special = false
  lower   = true
  upper   = false
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

locals {
  bucket_prefix = "${local.function_name}-${random_string.bucket_id_part.id}"
}

# data input to function
resource "google_storage_bucket" "input_bucket" {
  project                     = var.project_id
  name                        = coalesce(var.input_bucket_name, "${local.bucket_prefix}-input")
  location                    = var.region
  force_destroy               = var.bucket_force_destroy
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
      name, # avoid recreation of bucket just on name change alone
      labels
    ]
  }
}

# data output from function
module "output_bucket" {
  source = "../gcp-output-bucket"

  project_id                     = var.project_id
  bucket_write_role_id           = var.bucket_write_role_id
  function_service_account_email = google_service_account.service_account.email
  bucket_name_prefix             = coalesce(var.sanitized_bucket_name, local.bucket_prefix)
  bucket_name_suffix             = var.sanitized_bucket_name == null ? "-sanitized" : ""
  region                         = var.region
  expiration_days                = var.sanitized_expiration_days
  bucket_labels                  = var.default_labels
  bucket_force_destroy           = var.bucket_force_destroy
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

# the function's SA needs to be able to receive events from Eventarc, so can be triggered by GCS events
# project is most granular level for this role
# without this, we get "googleapi: Error 403: Validation failed for trigger {{TRIGGER_ID}}: Permission "eventarc.events.receiveEvent" denied on "{{SA_EMAIL}}"
# see: https://cloud.google.com/iam/docs/roles-permissions/eventarc#eventarc.eventReceiver
# historically, we used the GCP Compute Engine default service account as the event trigger SA, and relied on it having 'Editor' by default on most GCP projects
# but that does not seem to be universally true - so we'll stop relying on that assumption from 0.5.9 onwards
resource "google_project_iam_member" "grant_sa_event_receiver" {
  project = var.project_id
  member  = "serviceAccount:${google_service_account.service_account.email}"
  role    = "roles/eventarc.eventReceiver"
}

resource "google_cloud_run_service_iam_member" "grant_sa_invoker" {
  project  = var.project_id
  location = google_cloudfunctions2_function.function.location
  service  = google_cloudfunctions2_function.function.name
  member   = "serviceAccount:${google_service_account.service_account.email}"
  role     = "roles/run.invoker"
}

# to provision Cloud Function, TF must be able to act as the service account that the function will
# run as
resource "google_service_account_iam_member" "act_as" {
  member             = var.tf_runner_iam_principal
  role               = "roles/iam.serviceAccountUser"
  service_account_id = google_service_account.service_account.id
}

resource "google_cloudfunctions2_function" "function" {
  name        = local.function_name
  description = "Psoxy instance to process ${var.source_kind} files"
  project     = var.project_id
  location    = var.region

  build_config {
    runtime     = "java21"
    entry_point = "co.worklytics.psoxy.GCSFileEvent"

    docker_repository = var.artifact_repository_id

    source {
      storage_source {
        bucket = var.artifacts_bucket_name
        object = var.deployment_bundle_object_name
      }
    }
  }

  # NOTE: bulk connectors are STILL triggered through HTTPS invocations, so these have default HTTPS endpoint URls

  service_config {
    available_memory      = "${coalesce(var.available_memory_mb, 1024)}M"
    service_account_email = google_service_account.service_account.email
    timeout_seconds       = var.timeout_seconds
    ingress_settings      = "ALLOW_INTERNAL_ONLY"

    vpc_connector                 = var.vpc_config == null ? null : var.vpc_config.serverless_connector
    vpc_connector_egress_settings = var.vpc_config == null ? null : "ALL_TRAFFIC"

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
  }

  event_trigger {
    event_type            = "google.cloud.storage.object.v1.finalized"
    trigger_region        = var.region
    service_account_email = google_service_account.service_account.email

    # no retries for now, consistent with legacy behavior
    # can configure this to retry on errors, and will do so with exponential backoff. but concern is that
    # we have no control of that - eg no way to cap it at 3 or 5 retries, as far as can see atm
    retry_policy = "RETRY_POLICY_DO_NOT_RETRY"

    event_filters {
      attribute = "bucket"
      value     = google_storage_bucket.input_bucket.name
    }
  }

  labels = var.default_labels

  lifecycle {
    ignore_changes = [
      labels
    ]
  }

  # can't provision function until various IAM grants complete
  depends_on = [
    google_secret_manager_secret_iam_member.grant_sa_accessor_on_secret,
    google_service_account_iam_member.act_as,
    google_project_iam_member.grant_sa_event_receiver
  ]
}

locals {
  # For backwards compatibility with singular example_file
  example_file = var.example_file == null ? "/path/to/example/file.csv" : "${var.psoxy_base_dir}${var.example_file}"

  # Merge example_files list with singular example_file (if provided)
  all_example_files = concat(
    var.example_files,
    var.example_file != null ? [var.example_file] : []
  )

  # id that is unique for connector, within the environment (eg, files with this token in name, but otherwise equivalent, will not conflict)
  local_file_id = trimprefix(local.instance_id, var.environment_id_prefix)

  # whether this connector needs set up
  need_setup = var.instructions_template != null

  test_todo_step = var.todo_step + (local.need_setup ? 1 : 0)

  setup_todo_content = var.instructions_template == null ? "" : templatefile("${var.instructions_template}", {
    input_bucket_url = "gcs://${google_storage_bucket.input_bucket.name}"
  })
  todo_brief        = <<EOT
## Test ${local.function_name}
Check that the Psoxy works as expected and it transforms the files of your input bucket following
the rules you have defined:

```shell
node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f ${local.example_file} -d GCP -i ${google_storage_bucket.input_bucket.name} -o ${module.output_bucket.bucket_name}
```
EOT
  test_todo_content = <<EOT
# Testing Psoxy Bulk: ${local.function_name}

Review the deployed Cloud function in GCP console:

[Function in GCP Console](https://console.cloud.google.com/functions/details/${var.region}/${google_cloudfunctions2_function.function.name}?project=${var.project_id})

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

resource "local_file" "todo_setup" {
  count = (var.todos_as_local_files && local.need_setup) ? 1 : 0

  filename = "TODO ${var.todo_step} - setup ${local.local_file_id}.md"
  content  = local.setup_todo_content
}


resource "local_file" "todo_test_gcp_psoxy_bulk" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${local.test_todo_step} - test ${local.function_name}.md"
  content  = local.test_todo_content
}

resource "local_file" "test_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-${local.local_file_id}.sh"
  file_permission = "755"
  content         = <<EOT
#!/bin/bash
FILE_PATH=$${1:-${try(local.example_file, "")}}
BLUE='\e[0;34m'
NC='\e[0m'

printf "Quick test of $${BLUE}${local.function_name}$${NC} ...\n"

node ${var.psoxy_base_dir}tools/psoxy-test/cli-file-upload.js -f $FILE_PATH -d GCP -i ${google_storage_bucket.input_bucket.name} -o ${module.output_bucket.bucket_name}

if gzip -t "$FILE_PATH" 2>/dev/null; then
  printf "test file was compressed, so not testing compression as a separate case\n"
else
  printf "testing with compressed input file ... \n"
  # extract the file name from the path
  TEST_FILE_NAME=/tmp/$(basename $FILE_PATH)

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

output "example_files" {
  value = [
    for f in local.all_example_files : {
      path           = f
      content_base64 = base64encode(file("${var.psoxy_base_dir}${f}"))
    }
  ]
  description = "Array of example files with path relative to terraform config root and base64-encoded content"
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

output "todo_setup" {
  value = local.setup_todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "function_config" {
  description = "INTERNAL USE ONLY - Cloud Function configuration for CI/testing purposes. Users should NOT rely on this output's presence, structure, or schema as it may change without notice."
  value       = google_cloudfunctions2_function.function
}
