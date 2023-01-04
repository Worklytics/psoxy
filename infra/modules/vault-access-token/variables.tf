
variable "vault_policy_name" {
  type        = string
  description = "name of vault policy to grant to token"
}

variable "instance_id" {
  type        = string
  description = "id of proxy instance (eg, `psoxy-gcal`)"
}
