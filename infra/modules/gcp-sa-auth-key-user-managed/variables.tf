
variable "project_id" {
  type        = string
  description = "ID of GCP project to which service account belongs"
}

variable "service_account_id" {
  type        = string
  description = "ID of the service account"
}

variable "key_admin_group_email" {
  type        = string
  description = "email of Google group that will create keys for SA"
}

variable "secret_project" {
  type        = string
  description = "ID of project in which to store SA key as secret"
}

variable "secret_id" {
  type        = string
  description = "ID to give secret for SA key"
}

variable "replica_regions" {
  type        = list(string)
  description = "List of regions to replicate the secret to"
  default = [
    "us-east1",
    "us-west1",
  ]
}
