variable "tenant_location" {
  type        = string
  description = "The GCP multi-region where your Worklytics tenant is located."
  default     = "us"

  validation {
    condition     = can(regex("(?i)^(us|eu)$", var.tenant_location))
    error_message = "The `tenant_location` must be either 'us' or 'eu'."
  }
}
