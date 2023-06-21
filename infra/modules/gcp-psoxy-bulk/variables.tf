variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_id_prefix" {
  type        = string
  description = "A prefix to give to all resources created/consumed by this module."
  default     = "psoxy-"
}

variable "config_parameter_prefix" {
  type        = string
  description = "Prefix for psoxy config parameters, unique to environment"
  default     = null
}

variable "instance_id" {
  type        = string
  description = "id of psoxy instance, given environment"
  default     = null
}

variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}

variable "region" {
  type        = string
  description = "region into which to deploy function / its buckets"
  default     = "us-central1"
}

variable "source_kind" {
  type        = string
  description = "Kind of the content to process"
}

variable "secret_bindings" {
  type = map(object({
    secret_id      = string # NOT the full resource ID; just the secret_id within GCP project
    version_number = string # could be 'latest'
  }))
  description = "map of Secret Manager Secrets to expose to cloud function by ENV_VAR_NAME"
  default     = {}
}


variable "artifacts_bucket_name" {
  type        = string
  description = "Name of the bucket where artifacts are stored"
}

variable "deployment_bundle_object_name" {
  type        = string
  description = "Name of the object containing the deployment bundle"
}


variable "path_to_config" {
  type        = string
  description = "path to config file (usually something in ../../configs/, eg configs/gdirectory.yaml"
  default     = null
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides. Preferably a full path, /home/user/repos/, avoid tilde (~) shortcut to $HOME"
  default     = "../../.."
}

variable "environment_variables" {
  type        = map(string)
  description = "Non-sensitive values to add to functions environment variables; NOTE: will override anything in `path_to_config`"
  default     = {}
}

variable "bucket_write_role_id" {
  type        = string
  description = "The id of role to grant on bucket to enable writes"
}

variable "available_memory_mb" {
  type        = number
  description = "Memory (in MB), available to the function. Default value is 1024. Possible values include 128, 256, 512, 1024, etc."
  default     = 1024
}

variable "example_file" {
  type        = string
  description = "path to example file to use for testing, from psoxy_base_dir"
  default     = null
}

variable "input_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 30
}

variable "sanitized_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 720
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}
