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
  description = "Role to assume during script execution"
}

variable "login-ids" {
  type = map(string)
}