variable "identity_pool_id" {
  type        = string
  description = "Id of the identity pool to use"
}

variable "login" {
  type        = string
  description = "Login id to use"
}

variable "aws_region" {
  type        = string
  description = "AWS region to where to create the identity"
}