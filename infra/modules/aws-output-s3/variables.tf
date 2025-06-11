variable "environment_name" {
  type        = string
  description = "friendly qualifier to distinguish resources created by this terraform configuration other Terraform deployments, (eg, 'prod', 'dev', etc)"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-_ ]*[a-zA-Z0-9]$", var.environment_name))
    error_message = "The `environment_name` must start with a letter, can contain alphanumeric characters, hyphens, underscores, and spaces, and must end with a letter or number."
  }

  validation {
    condition     = !can(regex("^(?i)(aws|ssm)", var.environment_name))
    error_message = "The `environment_name` cannot start with 'aws' or 'ssm', as this will name your AWS resources with prefixes that displease the AMZN overlords."
  }
}

variable "instance_id" {
  type        = string
  description = "Human readable reference name for proxy instance, that uniquely identifies it given `environment_name`. Helpful for distinguishing resulting infrastructure"
}

variable "unique_sequence" {
  type = string
  description = "An optional 'unique sequence' to differentiate bucket names. Because bucket names must be globally unique, this is used to avoid collisions. If not provided, a random string will be generated.  We support providing it to aid grouping of instance buckets"
  default = null
}

variable "bucket_suffix" {
  type = string
  description = "An optional suffix to append to the bucket name. This can be used to further differentiate bucket names, especially if multiple side outputs are expected. If not provided, the default suffix will be used."
  default = "side-output"
}
