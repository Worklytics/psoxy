variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "invoker_sa_emails" {
  type        = list(string)
  description = "emails of GCP service accounts to allow to invoke ALL cloud functions in target project"
}

variable "bucket_location" {
  type        = string
  description = "location of bucket that will be used to store Psoxy artifacts"
  default     = "us-central-1"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."
}

variable "psoxy_version" {
  type        = string
  description = "version of psoxy to deploy"
  default     = "0.4.2"
}
