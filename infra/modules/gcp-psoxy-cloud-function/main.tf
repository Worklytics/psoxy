# deployment for a single Psoxy instance in GCP project that has be initialized for Psoxy.
# project itself may hold MULTIPLE psoxy instances, each with distinct values for `function_name`

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  for_each = var.secret_bindings

  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = each.value.secret_name
}

locals {
  secret_clauses = [for env_var_name, binding in var.secret_bindings : "${env_var_name}=${binding.secret_name}:${binding.version_number}" ]
}

resource "local_file" "todo" {
  filename = "TODO - deploy ${var.function_name}.md"
  content  = <<EOT
Run the following gcloud command from the root of the GCP implementation of Psoxy (eg,
`java/impl/gcp` within a checkout of the psoxy repo), after having run `mvn package` (you only need
to package once, but will run a similar deployment command for each psoxy instance).

```shell
gcloud beta functions deploy ${var.function_name} \
    --project=${var.project_id} \
    --runtime=java11 \
    --entry-point=co.worklytics.psoxy.Route \
    --trigger-http \
    --source=target/deployment \
    --service-account=${var.service_account_email} \
    --env-vars-file=configs/${var.source_kind}.yaml \
    --set-secrets '${join(",", local.secret_clauses)}'
```

and if you want to test from your local machine:
```shell
export PSOXY_GCP_PROJECT=${var.project_id}
export PSOXY_GCP_REGION=us-central1
```

EOT
}
