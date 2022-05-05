locals {
  grant_expires = timeadd(timestamp(), var.grant_duration)
}

# short-lived grant to human users to populate the token, as only needs to be set once
resource "google_secret_manager_secret_iam_member" "grant_secretVersionAdd_on_accessToken_to_users" {
  for_each = toset(var.user_emails)

  role      = "roles/secretmanager.secretVersionAdder"
  project   = var.project_id
  secret_id = var.secret_id
  member    = "user:${each.value}"

  condition {
    title       = "until ${local.grant_expires}"
    expression  = "request.time < timestamp(\"${local.grant_expires}\")"
    description = "short-lived grant to human users"
  }
}



resource "local_file" "TODO" {
  filename = "TODO - fill ${var.project_id} - ${var.secret_id} token.md"
  content  = <<EOT
Populate the token for ${var.secret_id} in GCP Secret Manager. Here are some ways to do
it using gcloud cmd line tool. YMMV.

```shell
gcloud secrets versions add ${var.secret_id} --project=${var.project_id} --data-file=/tmp/file-containing-accesstoken
```

```shell
printf "YOUR_TOKEN_HERE" | gcloud secrets versions add ${var.secret_id} --project=${var.project_id} --data-file=-
```

from macOS clipboard
```shell
pbpaste | gcloud secrets versions add ${var.secret_id} --project=${var.project_id} --data-file=-
```

reference: https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add

It's also possible to do it via GCP Console.
EOT
}
