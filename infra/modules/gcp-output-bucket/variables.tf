variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "region" {
  type        = string
  description = "region into which to bucket"
  default     = "us-central1"
}

variable "bucket_name_prefix" {
  type        = string
  description = "Prefix to prepend to randomized bucket name. Helpful for distinguishing resulting infrastructure"
  default     = ""

  # enforce max length to avoid bucket names that are too long
  validation {
    condition     = can(regex("^[a-z0-9\\-_]{0,40}$", var.bucket_name_prefix))
    error_message = "The `bucket_name_prefix` must be at most 40 characters."
  }
}

variable "bucket_name_suffix" {
  type        = string
  description = "Suffix to prepend to randomized bucket name. Helpful for distinguishing resulting infrastructure"
  default     = "-output"

  # enforce max length to avoid bucket names that are too long
  validation {
    condition     = can(regex("^[a-z0-9\\-_]{0,10}$", var.bucket_name_suffix))
    error_message = "The `bucket_name_suffix` must be at most 10 characters."
  }
}

variable "bucket_write_role_id" {
  type        = string
  description = "The id of role to grant on bucket to enable writes"

}

variable "function_service_account_email" {
  type        = string
  description = "The service account of the Cloud Function that will write to this bucket"

  validation {
    condition     = can(regex("^[a-zA-Z0-9-]+@[a-zA-Z0-9-]+.iam.gserviceaccount.com$", var.function_service_account_email))
    error_message = "The function_service_account_email must be valid Google service account email."
  }
}

variable "sanitizer_accessor_principals" {
  type        = list(string)
  description = "list of names of GCP principals"
  default     = []
}

# NOTE: while this value *can* be 0, that's the same as setting no expiration, which is OK except
# that GCP will not persist the lifecycle rule for the expiration, so Terraform will always show change
variable "expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire. If 0, no expiration is set but terraform will always show change in your plan."
  default     = 365 * 5 # 5 years
}
