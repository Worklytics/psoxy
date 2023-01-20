variable "instance_id" {
  type        = string
  description = "uniquely ids psoxy instance within AWS account"
}

variable "private_key_id" {
  type        = string
  description = "id of private key"
}

variable "private_key" {
  type        = string
  description = "value of the key"
}

variable "ssm_path" {
  type        = string
  description = "path/prefix in SSM Parameter Store at which to create parameters to hold keys"
}
