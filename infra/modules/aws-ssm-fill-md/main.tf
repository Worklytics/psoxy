variable "parameter_name" {
  type        = string
  description = "id of the secret"
}

variable "region" {
  type        = string
  description = "AWS region"
}

output "todo_markdown" {
  value = <<EOT
## Populate the token for ${var.parameter_name} in AWS Systems Manager Parameter Store.

### Using AWS cli

YMMV.
```shell
aws ssm put-parameter \
--region ${var.region} \
--name "${var.parameter_name}" \
--type "SecureString" \
--value "YOUR_VALUE_HERE" \
--overwrite
```

from macOS clipboard
```shell
pbpaste | aws ssm put-parameter \
--region ${var.region} \
--name "${var.parameter_name}" \
--type "SecureString" \
--value=- \
--overwrite
```

reference: https://cloud.google.com/sdk/gcloud/reference/secrets/versions/add

### AWS  Console

1. Visit

https://${var.region}.console.aws.amazon.com/systems-manager/parameters/${var.parameter_name}/description?region=${var.region}&tab=Table

2. Click "Edit"; paste your value in the 'Value' field and click 'Save changes'.

EOT
}
