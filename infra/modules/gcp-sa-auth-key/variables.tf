
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "rotation_days" {
  type        = number
  default     = 60
  description = "rotation period for the SA key, in days"
}

variable "tf_runner_email" {
  type        = string
  description = "Email address of the Terraform Cloud runner (SA/user terraform is running as, if already known.  If omitted, will attempt to detect."
  default     = null
}
