variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-central1"
}

variable "environment_id_prefix" {
  type        = string
  description = "A prefix to give to all resources created/consumed by this module."
  default     = "psoxy-"
}

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}

variable "instance_id" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "config_parameter_prefix" {
  type        = string
  description = "Prefix for psoxy config parameters"
  default     = null
}

variable "service_account_email" {
  type        = string
  description = "email of the service account that the cloud function will run as"
}

variable "secret_bindings" {
  type = map(object({
    secret_id      = string # NOT the full resource ID; just the secret_id within GCP project
    version_number = string # could be 'latest'
  }))
  description = "map of Secret Manager Secrets to expose to cloud function by ENV_VAR_NAME"
  default     = {}
}

variable "secret_replica_locations" {
  type        = list(string)
  description = "list of locations to replicate secrets to. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1"
  ]
}

variable "artifacts_bucket_name" {
  type        = string
  description = "Name of the bucket where artifacts are stored"
}

variable "deployment_bundle_object_name" {
  type        = string
  description = "Name of the object containing the deployment bundle"
}

variable "artifact_repository_id" {
  type        = string
  description = "(NOTE: it will be available since 0.5 psoxy version) ID of the artifact repository"
  default     = null
}

variable "path_to_repo_root" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."
}

variable "path_to_config" {
  type        = string
  description = "DEPRECATED; path to config file (usually something in ../../configs/, eg configs/gdirectory.yaml"
  default     = null
}

variable "target_host" {
  type        = string
  description = "The target host to which to forward requests."
  default     = null # for v0.4, this is optional; assumed to be in config if not defined here
}

variable "source_auth_strategy" {
  type        = string
  description = "The authentication strategy to use when connecting to the source."
  default     = null # for v0.4, this is optional; assumed to be in config if not defined here
}

variable "oauth_scopes" {
  type        = list(string)
  description = "The OAuth scopes to use when connecting to the source."
  default     = []
}

variable "example_api_calls" {
  type        = list(string)
  description = "example endpoints that can be called via proxy"
  default     = []
}

variable "example_api_requests" {
  type = list(object({
    method       = optional(string, "GET")
    path         = string
    content_type = optional(string, "application/json")
    body         = optional(string, null)
  }))
  description = "example API requests with method, content_type and body parameters that can be called via proxy"
  default     = []
}

variable "example_api_calls_user_to_impersonate" {
  type        = string
  description = "if example endpoints require impersonation of a specific user, use this id"
  default     = null
}

variable "environment_variables" {
  type        = map(string)
  description = "Non-sensitive values to add to functions environment variables; NOTE: will override anything in `path_to_config`"
  default     = {}
}

variable "source_kind" {
  type        = string
  description = "kind of source to which you're connecting"
  default     = "unknown"
}

variable "invoker_sa_emails" {
  type        = list(string)
  description = "emails of GCP service accounts to allow to invoke this proxy instance via HTTP"
  default     = []
}

variable "available_memory_mb" {
  type        = number
  description = "Memory (in MB), available to the function. Default value is 1024. Possible values include 128, 256, 512, 1024, etc."
  default     = 1024
}

variable "gcp_principals_authorized_to_test" {
  type        = list(string)
  description = "list of GCP principals authorized to test this deployment - eg 'user:alice@acme.com', 'group:devs@acme.com'; if omitted, up to you to configure necessary perms for people to test if desired."
  default     = []
}

variable "side_output_original" {
  type = object({
    bucket          = optional(string, null),     # if omitted, a bucket will be created
    allowed_readers = optional(list(string), []), # a list of GCP principals that should be allowed to read the bucket
  })
  description = "**ALPHA** Defines a side output from the instance for original data, as it was received from API."
  default     = null
}

variable "side_output_sanitized" {
  type = object({
    bucket          = optional(string, null),     # if omitted, a bucket will be created
    allowed_readers = optional(list(string), []), # a list of GCP principals that should be allowed to read the bucket
  })
  description = "**ALPHA** Defines a side output from the instance for sanitized data."
  default     = null
}

variable "bucket_write_role_id" {
  type        = string
  description = "The id of role to grant on bucket to enable writes. REQUIRED if configuring `side_output`"
  default     = null
}


variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "enable_async_processing" {
  type        = bool
  description = "whether to enable async processing for this connector"
  default     = false
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}

variable "vpc_config" {
  type = object({
    serverless_connector = string # Format: projects/{project}/locations/{location}/connectors/{connector}
  })
  description = "VPC configuration for the Cloud Run function."
  default     = null

  validation {
    condition = (
      var.vpc_config == null ||
      can(regex("^projects/[^/]+/locations/[^/]+/connectors/[^/]+$", var.vpc_config.serverless_connector))
    )
    error_message = "If vpc_config.serverless_connector is provided, it must match the format: projects/{project}/locations/{location}/connectors/{connector}"
  }
}
