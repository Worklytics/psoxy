variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "secret_id" {
  type        = string
  description = "id of the secret"
}
variable "path_prefix" {
  type        = string
  description = "A prefix to add to the secret path."
  default     = ""
}

locals {
  full_secret_id = "${var.path_prefix}${var.secret_id}"
}

output "todo_markdown" {
  value = <<EOT
## Populate the token for ${local.full_secret_id} in GCP Secret Manager.


### Using `gcloud`

YMMV.
```shell
gcloud secrets versions add ${local.full_secret_id} --project=${local.full_secret_id} --data-file=/tmp/file-containing-accesstoken
```

```shell
printf "YOUR_SECRET_VALUE_HERE" | gcloud secrets versions add ${local.full_secret_id} --project=${var.project_id} --data-file=-
```

from macOS clipboard
```shell
pbpaste | gcloud secrets versions add ${local.full_secret_id} --project=${var.project_id} --data-file=-
```

reference: https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add

### GCP Console

1. Visit

https://console.cloud.google.com/security/secret-manager/secret/${local.full_secret_id}/versions?project=${var.project_id}

2. Click "+NEW VERSION"; paste your value or upload it as a file. Click 'ADD NEW VERSION' to upload it.

EOT
}
