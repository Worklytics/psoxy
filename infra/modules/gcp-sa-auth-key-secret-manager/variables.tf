
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

variable "secret_project" {
  type        = string
  description = "ID of project in which to store SA key as secret"
}

variable "path_prefix" {
  type        = string
  description = "A prefix to add to the secret path."
  default     = ""
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

variable "replica_regions" {
  type        = list(string)
  description = "List of regions to replicate the secret to"
  default = [
    "us-east1",
    "us-west1",
  ]
}
