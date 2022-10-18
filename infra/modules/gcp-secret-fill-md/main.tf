variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "secret_id" {
  type        = string
  description = "id of the secret"
}

output "todo_markdown" {
  value = <<EOT
## Populate the token for ${var.secret_id} in GCP Secret Manager.


### Using `gcloud`

YMMV.
```shell
gcloud secrets versions add ${var.secret_id} --project=${var.project_id} --data-file=/tmp/file-containing-accesstoken
```

```shell
printf "YOUR_SECRET_VALUE_HERE" | gcloud secrets versions add ${var.secret_id} --project=${var.project_id} --data-file=-
```

from macOS clipboard
```shell
pbpaste | gcloud secrets versions add ${var.secret_id} --project=${var.project_id} --data-file=-
```

reference: https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add

### GCP Console

1. Visit

https://console.cloud.google.com/security/secret-manager/secret/${var.secret_id}/versions?project=${var.project_id}

2. Click "+NEW VERSION"; paste your value or upload it as a file. Click 'ADD NEW VERSION' to upload it.

EOT
}
