
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "secret_id" {
  type        = string
  description = "ID to give secret for SA key"
}

variable "rotation_days" {
  type        = number
  default     = 60
  description = "rotation period for the SA key, in days"
}

variable "kms_key_id" {
  type        = string
  description = "KMS key ID or ARN to use for encrypting secrets. If not provided, secrets will be encrypted by SSM with its keys (controlled by AWS)."
  default     = null
}
