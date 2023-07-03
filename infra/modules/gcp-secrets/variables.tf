variable "secrets" {
  type = map(
    object({
      value       = string
      description = string
    })
  )
}


variable "replica_regions" {
  type        = list(string)
  description = "DEPRECATED; use `replica_locations`. List of locations to which to replicate secrets. See https://cloud.google.com/secret-manager/docs/locations"
  default = null
}

variable "replica_locations" {
  type        = list(string)
  description = "List of locations to which to replicate GCP Secret Manager secrets. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1",
  ]
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

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}
