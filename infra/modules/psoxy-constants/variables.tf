variable "environment_name" {
  type        = string
  description = "friendly qualifier to distinguish resources created by this terraform configuration other Terraform deployments, (eg, 'prod', 'dev', etc)"
  default     = "psoxy"


  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9]$", var.environment_name))
    error_message = "The `environment_name` must start with a letter, can contain alphanumeric characters, hyphens, underscores, and spaces, and must end with a letter or number."
  }

  validation {
    condition     = !can(regex("^(?i)(aws|ssm)", var.environment_name))
    error_message = "The `environment_name` cannot start with 'aws' or 'ssm', as this will name your AWS resources with prefixes that displease the AMZN overlords."
  }
}
