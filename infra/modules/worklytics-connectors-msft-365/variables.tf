variable "enabled_connectors" {
  type        = list(string)
  description = "ids of connectors to enable"
}

variable "environment_id" {
  type        = string
  description = "Qualifier to append to names/ids of resources. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = "psoxy"

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_id))
    error_message = "The environment_name must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "msft_tenant_id" {
  type        = string
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  default     = ""
}

variable "example_msft_user_guid" {
  type        = string
  description = "example MSFT user guid (uuid) for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_USER_GUID}"
}

variable "msft_owners_email" {
  type        = set(string)
  description = "(Only if config includes MSFT connectors). Optionally, set of emails to apply as owners on AAD apps apart from current logged user"
  default     = []
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}
