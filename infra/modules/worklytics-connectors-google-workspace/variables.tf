variable "environment_id" {
  type        = string
  description = "Qualifier to append to names/ids of resources. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = "psoxy"

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_id))
    error_message = "The environment_name must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "enabled_connectors" {
  type        = list(string)
  description = "ids of connectors to enable"
}

variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host OAuth Clients for Google Workspace API connectors"
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

variable "google_workspace_example_user" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for none)"
  default     = null
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}

variable "provision_gcp_sa_keys" {
  type        = bool
  description = "whether to provision key for each connector's GCP Service Account (OAuth Client). If false, you must create the key manually and provide it."
  default     = true
}

variable "gcp_sa_key_rotation_days" {
  type        = number
  description = "rotation period for the GCP Service Account key, in day; not applicable if provision_gcp_sa_keys is false"
  default     = 60

  validation {
    condition     = var.gcp_sa_key_rotation_days > 0
    error_message = "gcp_sa_key_rotation_days must be greater than 0"
  }
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}
