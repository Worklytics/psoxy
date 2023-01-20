
variable "aws_auth_backend_name" {
  type        = string
  description = "name of AWS auth backend set up in Vault (see aws-vault-auth)"
}

variable "instance_id" {
  type        = string
  description = "id of proxy instance (eg, `psoxy-gcal`)"
}

variable "role_arn" {
  type        = string
  description = "ARN of proxy instance's execution role"
}

variable "vault_policy_name" {
  type        = string
  description = "name of Vault policy to bind to AWS role"
}
