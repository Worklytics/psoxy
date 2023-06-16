variable "vault_addr" {
  type        = string
  description = "address of your Vault instance"
  default     = null # leave null if not using Vault
}

variable "aws_vault_role_arn" {
  type        = string
  description = "ARN of vault role; see https://developer.hashicorp.com/vault/docs/auth/aws"
  default     = null # leave null if not using Vault
}
