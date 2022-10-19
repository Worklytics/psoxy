
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "rotation_days" {
  type        = number
  default     = 60
  description = "rotation period for the SA key, in days"
}
