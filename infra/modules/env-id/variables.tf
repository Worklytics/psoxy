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

variable "max_length" {
  type        = number
  description = "a max length for which to generate id, if any"
  default     = null

  validation {
    condition     = var.max_length == null || coalesce(var.max_length, 10) >= 10
    error_message = "The `max_length` must be at least 10."
  }
}

variable "preferred_word_delimiter" {
  type        = string
  description = "the preferred word delimiter to use to separate words in the id, if any"
  default     = "-"

  validation {
    condition     = can(regex("^[-_ ]{1}$", var.preferred_word_delimiter))
    error_message = "The `preferred_word_delimiter` must be hyphen, underscore, or space."
  }
}

variable "supported_word_delimiters" {
  type        = list(string)
  description = "A list of word delimiters to allow in the id, if less than default."
  default     = ["-", "_"]

  validation {
    condition     = alltrue([for item in var.supported_word_delimiters : contains(["-", " ", "_"], item)])
    error_message = "`supported_word_delimiters` can only contain hyphen, underscore, and/or space."
  }
}
