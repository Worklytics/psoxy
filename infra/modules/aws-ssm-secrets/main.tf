# stores secrets as AWS SSM Parameters
# NOTE: value of this module is a consistent interface across potential Secret store implementations
#   eg, GCP Secret Manager, AWS SSM Parameter Store, Hashicorp Vault, etc.
#  but is this good Terraform style? clearly in AWS case, this module doesn't do much ...

resource "aws_ssm_parameter" "secret" {
  for_each   = var.secrets

  name        = each.key
  type        = "SecureString"
  description = each.value.description
  value       = sensitive(each.value.value)

  lifecycle {
    ignore_changes = [
      tags,
      value
    ]
  }
}

# for use in explicit IAM policy grants?
# q: good idea? breaks notion of AWS SSM parameters secrets being an implementation of a generic
# secrets-store interface
# q: is to ALSO pass in some notion of access? except very different per implementation
output "secret_ids" {
  value = { for k in var.secrets : k => aws_ssm_parameter.secret[k].id }
}

