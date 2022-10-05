
variable "source_id" {
  type        = string
  description = "The ID of the source to use for the data"
}

variable "connector_specific_external_steps" {
  type        = string
  description = "Text explaining the steps that must be completed outside Terraform for the connector; markdown."
}

variable "host_cloud" {
  type        = string
  description = "The host cloud provider to configure. 'aws' or 'gcp'."

  validation {
    condition     = can(regex(var.host_cloud, "^(aws|gcp)$"))
    error_message = "The host cloud provider must be 'aws' or 'gcp'."
  }
}

variable "token_secret_id" {
  type        = string
  description = "The ID of the secret containing the token secret"
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}
