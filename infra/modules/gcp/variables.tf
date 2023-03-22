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

  validation {
    condition     = fileexists(format("%sjava/pom.xml", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be a path to a directory containing java/pom.xml."
  }
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}

variable "psoxy_version" {
  type        = string
  description = "IGNORED; version of psoxy to deploy"
  default     = null
}
