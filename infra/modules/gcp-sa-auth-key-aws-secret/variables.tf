
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "tf_gcp_principal_email" {
  description = "if terraform is using gcloud cli authenticated a known principal (eg, user or service account), pass it in here; this avoids need to try to determine it dynamically at run-time. If it ends with 'iam.gserviceaccount.com', it will be treated as a service account; otherwise assumed to be a regular Google user."
  type        = string
  default     = null

  validation {
    condition     = var.tf_gcp_principal_email == null || can(regex(".*@.*", var.tf_gcp_principal_email))
    error_message = "The tf_gcp_principal_email value should be a valid email address."
  }
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
