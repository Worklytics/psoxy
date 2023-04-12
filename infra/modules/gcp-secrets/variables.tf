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
  description = "List of regions to replicate the secret to"
  default = [
    "us-east1",
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
