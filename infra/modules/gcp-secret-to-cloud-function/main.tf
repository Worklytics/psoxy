# expose a Secret Manager secret to a Cloud function

locals {
  secret_version_number = trimprefix(var.secret_version_name, "${var.secret_name}/versions/")
  slugified_secret_name = replace(var.secret_name, "/", "-")
}

resource "google_secret_manager_secret_iam_member" "grant_sa_accessor_on_secret" {
  member    = "serviceAccount:${var.service_account_email}"
  role      = "roles/secretmanager.secretAccessor"
  secret_id = var.secret_name
}

# terraform
resource "local_file" "todo" {
  filename = "TODO ${var.function_name} - deploy ${local.slugified_secret_name}.md"
  content  = <<EOT

gcloud functions deploy ${var.function_name} \
    --entry-point=co.worklytics.psoxy.Route \
    --runtime=java11 \
    --trigger-http \
    --source=target/deployment \
    --project=${var.project_id} \
    --service-account=${var.service_account_email} \
    --env-vars-file=config.yaml \
    --update-secrets 'SERVICE_ACCOUNT_KEY=${var.secret_name}:${local.secret_version_number}'
```
EOT
}
