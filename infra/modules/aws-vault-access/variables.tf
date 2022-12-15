
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

variable "path_to_global_secrets" {
  type    = string
  default = "secret/PSOXY_GLOBAL"
}

variable "path_to_instance_secrets" {
  type    = string
  default = null
}
