
variable "service_account_id" {
  type        = string
  description = "ID of the service account"
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

variable "tf_runner_email" {
  type        = string
  description = "Email address of the Terraform Cloud runner (SA/user terraform is running as, if already known.  If omitted, will attempt to detect."
  default     = null
}
