variable "identity_pool_id" {
  type        = string
  description = "Id of the identity pool to use"
}

variable "aws_region" {
  type        = string
  description = "AWS region to where to create the identity"
}

variable "aws_role" {
  type        = string
  description = "If provided, role to assume during script execution"
  default     = ""
}

# TODO: expect value format "${module.cognito-identity-pool.developer_provider_name}=${module.msft-connection[k].connector.application_id}"
# --> add validation of that OR split those components in properties of an object??
variable "login_ids" {
  type        = map(string)
  description = "Map of connector id => login id for which to create identities in pool"
}