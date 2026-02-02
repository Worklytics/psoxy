variable "project_id" {
  type        = string
  description = "name of the gcp project"
}

variable "tf_runner_iam_principal" {
  description = "The IAM principal (e.g., 'user:alice@example.com' or 'serviceAccount:terraform@project.iam.gserviceaccount.com') that Terraform is running as, used for granting necessary permissions to provision Cloud Functions."
  type        = string

  validation {
    condition     = can(regex("^(user:|serviceAccount:|group:|domain:).*", var.tf_runner_iam_principal))
    error_message = "The tf_runner_iam_principal value should be a valid IAM principal (e.g., 'user:alice@example.com' or 'serviceAccount:terraform@project.iam.gserviceaccount.com')."
  }
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

variable "webhook_batch_invoker_sa_email" {
  type        = string
  description = "email of the service account that will invoke the batch processing function"
  default     = null
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


# examples:
# `gcp-kms:projects/{project}/locations/{location}/keyRings/{keyRing}/cryptoKeys/{key}/cryptoKeyVersions/{version}`
# `base64:BASE64_ENCODED_PUBLIC_KEY` - must be RSA public key in base64 format
variable "webhook_auth_public_keys" {
  type        = list(string)
  description = "list of public keys to use for verifying webhook signatures; if empty, no signature verification will be performed. see docs for schema"
  default     = []
}

variable "provision_auth_key" {
  type = object({
    rotation_days = optional(number, null)                         # null means no rotation; if > 0, will rotate every N days
    key_spec      = optional(string, "RSA_SIGN_PKCS1_2048_SHA256") # see https://cloud.google.com/kms/docs/reference/rest/v1/CryptoKeyVersionAlgorithm
  })
  description = "if provided, will module will provision a public-private key pair for authenticating webhooks and signing payloads for integrity checks. the id of the key pair will be exposed as an output, and the public-key configured as accepted auth key in the lambda"
  default     = null

  validation {
    condition = (
      var.provision_auth_key == null ||
      (
        try(var.provision_auth_key.rotation_days, null) == null ||
        try(var.provision_auth_key.rotation_days, 0) > 0
      )
    )
    error_message = "If `provision_auth_key` is provided, `rotation_days` must be a positive number or null."
  }
}

variable "key_ring_id" {
  type        = string
  description = "id of KMS key ring on which to provision any required KMS keys; REQUIRED if `provision_auth_key` is provided"
  default     = null

  validation {
    condition = (
      var.key_ring_id == null || can(regex("^projects/[^/]+/locations/[^/]+/keyRings/[^/]+$", var.key_ring_id))
    )
    error_message = "If `key_ring_id` is provided, it must be a valid long-form KMS key ring ID (matching `^projects/[^/]+/locations/[^/]+/keyRings/[^/]+$`)."
  }
}


variable "http_methods" {
  type        = list(string)
  description = "HTTP methods to expose; NOTE: 'OPTIONS' is always added to this list, so you don't need to include it; if you want to allow all methods, use ['*']"
  default     = ["POST"]
}


variable "secret_replica_locations" {
  type        = list(string)
  description = "list of locations to replicate secrets to. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1"
  ]
}

variable "allow_origins" {
  type        = list(string)
  description = "list of origins to allow for CORS, eg 'https://my-app.com'; if you want to allow all origins, use ['*'] (the default)"
  default     = ["*"]
}

variable "rules_file" {
  type        = string
  description = "Path to the file containing the rules for the webhook collector"
}

variable "oidc_token_verifier_role_id" {
  type        = string
  description = "Role to grant on crypto key(s) used to sign OIDC tokens (used to authenticate requests to webhook collectors). Only provisioned if support_webhook_collectors is true."
}

variable "batch_processing_frequency_minutes" {
  type        = number
  description = "Frequency (in minutes) at which to batch process webhooks"
  default     = 5

  validation {
    condition = (
      var.batch_processing_frequency_minutes >= 1 && var.batch_processing_frequency_minutes <= 30
    )
    error_message = "`batch_processing_frequency_minutes` must be between 1 and 30."
  }
}

variable "example_payload" {
  type        = string
  description = "Example payload content to use for testing; if provided, will be used in the test script."
  default     = null
}

variable "example_identity" {
  type        = string
  description = "Example identity to use for testing; if provided, will be used in the test script."
  default     = null
}

variable "output_path_prefix" {
  type        = string
  description = "optional path prefix to prepend to webhook output files in the bucket (e.g., 'events_', 'webhooks/')"
  default     = ""
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
