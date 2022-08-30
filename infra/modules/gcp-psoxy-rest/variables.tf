variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "region" {
  type        = string
  description = "region into which to deploy function"
  default     = "us-central1"
}

variable "instance_id" {
  type        = string
  description = "kind of source (eg, 'gmail', 'google-chat', etc)"
}

variable "service_account_email" {
  type        = string
  description = "email of the service account that the cloud function will run as"
}

variable "secret_bindings" {
  type = map(object({
    secret_name    = string
    version_number = string
  }))
  description = "map of Secret Manager Secrets to expose to cloud function (ENV_VAR_NAME --> resource ID of GCP secret)"
}

variable "artifacts_bucket_name" {
  type        = string
  description = "Name of the bucket where artifacts are stored"
}

variable "deployment_bundle_object_name" {
  type        = string
  description = "Name of the object containing the deployment bundle"
}

variable "path_to_repo_root" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."
}

variable "path_to_config" {
  type        = string
  description = "path to config file (usually something in ../../configs/, eg configs/gdirectory.yaml"
}

variable "salt_secret_id" {
  type        = string
  description = "Id of the secret used to salt pseudonyms"
}

variable "salt_secret_version_number" {
  type        = string
  description = "Version number of the secret used to salt pseudonyms"
}

variable "example_api_calls" {
  type        = list(string)
  description = "example endpoints that can be called via proxy"
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

