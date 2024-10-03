variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}


variable "bucket_location" {
  type        = string
  description = "location of bucket that will be used to store Psoxy artifacts"
  default     = "us-central-1"
}

variable "secret_replica_locations" {
  type        = list(string)
  description = "List of locations to which to replicate secrets. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1",
  ]
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

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one)"
  default     = null
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

variable "environment_id_prefix" {
  type        = string
  description = "A prefix to give to all resources created/consumed by this module."
  default     = ""
}

variable "config_parameter_prefix" {
  type        = string
  description = "A prefix to give to all config parameters (GCP Secret Manager Secrets) created/consumed by this module."
  default     = ""
}

variable "install_test_tool" {
  type        = bool
  description = "whether to install the test tool (can be 'false' if Terraform not running from a machine where you intend to run tests of your Psoxy deployment)"
  default     = true
}

variable "custom_artifacts_bucket_name" {
  type        = string
  description = "name of bucket to use for custom artifacts, if you want something other than default"
  default     = null
}

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}
