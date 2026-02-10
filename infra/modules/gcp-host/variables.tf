variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "tf_gcp_principal_email" {
  description = "if terraform is using gcloud cli authenticated a known principal (eg, user or service account), pass it in here; this avoids need to try to determine it dynamically at run-time. If it ends with 'iam.gserviceaccount.com', it will be treated as a service account; otherwise assumed to be a regular Google user."
  type        = string
  default     = null

  validation {
    condition     = var.tf_gcp_principal_email == null || can(regex(".*@.*", var.tf_gcp_principal_email))
    error_message = "The tf_gcp_principal_email value should be a valid email address."
  }
}

variable "environment_name" {
  type        = string
  description = "Qualifier to append to names/ids of resources for psoxy. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = "psoxy"

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_name))
    error_message = "The environment_name must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "config_parameter_prefix" {
  type        = string
  description = "A prefix to give to all config parameters (GCP Secret Manager Secrets) created/consumed by this module. If omitted, and `environment_id` provided, that will be used."
  default     = ""

  # taken from https://cloud.google.com/secret-manager/docs/reference/rpc/google.cloud.secrets.v1beta1
  # secret IDs can be up to 255 chars, so limit prefix to 120 gives plenty of leeway
  validation {
    condition     = can(regex("^[A-z0-9\\-_]{0,120}$", var.config_parameter_prefix))
    error_message = "The config_parameter_prefix must be 0-120 chars of [A-z0-9\\-_] only."
  }
}

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}

variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"

  validation {
    condition     = can(regex(".*\\/$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should end with a slash."
  }
  validation {
    condition     = can(regex("^[^~].*$", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be absolute path (not start with ~)."
  }
}

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one). Can be a local file path or GCS URL (e.g., 'gs://psoxy-public-artifacts/psoxy-0.4.28.zip')."
  default     = null
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}

variable "install_test_tool" {
  type        = bool
  description = "whether to install the test tool (can be 'false' if Terraform not running from a machine where you intend to run tests of your Psoxy deployment)"
  default     = true
}

variable "gcp_principals_authorized_to_test" {
  type        = list(string)
  description = "list of GCP principals authorized to test this deployment - eg 'user:alice@acme.com', 'group:devs@acme.com'; if omitted, up to you to configure necessary perms for people to test if desired."
  default     = []
}

variable "provision_testing_infra" {
  type        = bool
  description = "Whether to provision infra needed to support testing of deployment. If false, it's left to you to ensure the GCP principal you use when running test scripts has the correct permissions."
  default     = true
}

variable "general_environment_variables" {
  type        = map(string)
  description = "environment variables to add for all connectors"
  default     = {}
}

variable "pseudonymize_app_ids" {
  type        = string
  description = "if set, will set value of PSEUDONYMIZE_APP_IDS environment variable to this value for all sources"
  default     = true
}

variable "email_canonicalization" {
  type        = string
  description = "defines how email address are processed prior to hashing, hence which are considered 'canonically equivalent'; one of 'STRICT' (default and most standard compliant) or 'IGNORE_DOTS' (probably most in line with user expectations)"
  default     = "STRICT"
}

variable "gcp_region" {
  type        = string
  description = "Region in which to provision GCP resources, if applicable"
  default     = "us-central1"
}

variable "secret_replica_locations" {
  type        = list(string)
  description = "List of locations to which to replicate GCP Secret Manager secrets. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1",
  ]
}


variable "vpc_config" {
  type = object({
    network              = optional(string)           # VPC network for Direct VPC Egress (required if `serverless_connector` is not provided). Can be simple name or full self-link
    subnet               = optional(string)           # VPC subnet for Direct VPC Egress (required if `serverless_connector` is not provided)
    network_tags         = optional(list(string), []) # Network tags for Direct VPC Egress firewall rules
    serverless_connector = optional(string)           # Format: projects/{project}/locations/{location}/connectors/{connector} - if provided, uses Serverless VPC Access connector instead of Direct VPC Egress
  })

  description = "**alpha** configuration of a VPC to be used by the Psoxy instances, if any (null for none). If network and subnet are provided without serverless_connector, Direct VPC Egress will be used. If serverless_connector is provided, that connector will be used."
  default     = null
  # serverless_connector: allow null; if provided, must match the full resource name
  validation {
    condition = (
      var.vpc_config == null
      || try(var.vpc_config.serverless_connector, null) == null
      || can(regex("^projects/[^/]+/locations/[^/]+/connectors/[^/]+$", try(var.vpc_config.serverless_connector, "")))
    )
    error_message = "If vpc_config.serverless_connector is provided, it must match the format: projects/{project}/locations/{location}/connectors/{connector}"
  }

  validation {
    condition = (
      var.vpc_config == null
      || try(var.vpc_config.serverless_connector, null) != null
      ||
      (
        # Accepts a simple network name: lowercase letters, digits, dashes
        can(regex("^[a-z0-9-]+$", try(var.vpc_config.network, "")))
        ||
        # Accepts a full self-link (Compute URL format)
        can(regex("^projects/[^/]+/(global|regions/[^/]+)/networks/[^/]+$", try(var.vpc_config.network, "")))
      )
    )
    error_message = "vpc_config.network must be lowercase letters, numbers, or dashes, or a full self-link."
  }

  validation {
    condition = (
      var.vpc_config == null
      || try(var.vpc_config.serverless_connector, null) != null
      || (try(var.vpc_config.network, null) != null && try(var.vpc_config.subnet, null) != null)
    )
    error_message = "If vpc_config is provided without serverless_connector, both network and subnet are required for Direct VPC Egress."
  }
}

variable "kms_key_ring" {
  type        = string
  description = "name of KMS key ring on which to provision any required KMS keys; if omitted, one will be created for you"
  default     = null
}

variable "custom_artifacts_bucket_name" {
  type        = string
  description = "name of bucket to use for custom artifacts, if you want something other than default"
  default     = null
}

variable "api_connectors" {
  type = map(object({
    source_kind             = string
    source_auth_strategy    = string
    target_host             = string
    oauth_scopes_needed     = optional(list(string), [])
    environment_variables   = optional(map(string), {})
    enable_async_processing = optional(bool, false)
    example_api_calls       = optional(list(string), [])
    example_api_requests = optional(list(object({
      method       = optional(string, "GET")
      path         = string
      content_type = optional(string, "application/json")
      body         = optional(string, null)
    })), [])
    example_api_calls_user_to_impersonate = optional(string)
    secured_variables = optional(list(object({
      name                = string
      value               = optional(string)
      writable            = optional(bool, false)
      lockable            = optional(bool, false)
      sensitive           = optional(bool, true)
      value_managed_by_tf = optional(bool, true)
      description         = optional(string)
      })),
    [])
    settings_to_provide = optional(map(string), {})
    rules_file          = optional(string, null)
  }))

  description = "map of API connectors to provision"
}

# q: better to flatten this into connectors themselves?
variable "custom_api_connector_rules" {
  type        = map(string)
  description = "map of connector id --> YAML file with custom rules"
  default     = {}
}

variable "webhook_collectors" {
  type = map(object({
    rules_file = string
    provision_auth_key = optional(object({                           # whether to provision auth keys for webhook collector; if not provided, will not provision any
      rotation_days = optional(number, null)                         # null means no rotation; if > 0, will rotate every N days
      key_spec      = optional(string, "RSA_SIGN_PKCS1_2048_SHA256") # see https://cloud.google.com/kms/docs/reference/rest/v1/CryptoKeyVersionAlgorithm
    }), null)
    auth_public_keys                   = optional(list(string), [])    # list of public keys to use for verifying webhook signatures; if empty AND no auth keys provision, no app-level auth will be done
    allow_origins                      = optional(list(string), ["*"]) # list of origins to allow for CORS, eg 'https://my-app.com'; if you want to allow all origins, use ['*'] (the default)
    batch_processing_frequency_minutes = optional(number, 5)           # frequency (in minutes) at which to batch process webhooks
    output_path_prefix                 = optional(string, "")          # optional path prefix to prepend to webhook output files in bucket

    example_identity = optional(string, null) # example identity to use in test payloads
    example_payload  = optional(string, null) # example payload content to use in test scripts
  }))
  default = {}

  description = "map of webhook collector id --> webhook collector configuration"
}

variable "bulk_connectors" {
  type = map(object({
    source_kind           = string
    input_bucket_name     = optional(string) # allow override of default bucket name
    sanitized_bucket_name = optional(string) # allow override of default bucket name
    rules = optional(object({
      pseudonymFormat                = optional(string)
      columnsToRedact                = optional(list(string), [])
      columnsToInclude               = optional(list(string), null)
      columnsToPseudonymize          = optional(list(string), [])
      columnsToPseudonymizeIfPresent = optional(list(string), null)
      columnsToDuplicate             = optional(map(string), {})
      columnsToRename                = optional(map(string), {})
      fieldsToTransform = optional(map(object({
        newName    = string
        transforms = optional(list(map(string)), [])
      })))
    }))
    rules_file            = optional(string)
    example_file          = optional(string)
    instructions_template = optional(string)
    settings_to_provide   = optional(map(string), {})
    available_memory_mb   = optional(number)
  }))

  description = "map of connector id  => bulk connectors to provision"
}

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
}

variable "bulk_input_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 30
}

variable "bulk_sanitized_expiration_days" {
  type        = number
  description = "**alpha** Number of days after which objects in the bucket will expire"
  default     = 720
}

# q: move this into custom_bulk_connector_args
variable "custom_bulk_connector_rules" {
  type = map(object({
    pseudonymFormat                = optional(string, "URL_SAFE_TOKEN")
    columnsToRedact                = optional(list(string), [])
    columnsToInclude               = optional(list(string))
    columnsToPseudonymize          = optional(list(string), [])
    columnsToPseudonymizeIfPresent = optional(list(string))
    columnsToDuplicate             = optional(map(string))
    columnsToRename                = optional(map(string))
    fieldsToTransform = optional(map(object({
      newName    = string
      transforms = optional(list(map(string)), [])
    })))
  }))

  description = "map of connector id --> rules object"
  default     = {}
}

variable "custom_bulk_connector_arguments" {
  type = map(object({
    available_memory_mb = optional(number)
    timeout_seconds     = optional(number)
  }))

  description = "map of connector id --> settings object"
  default     = {}
}

variable "custom_side_outputs" {
  type = map(object({
    ORIGINAL  = optional(string, null),
    SANITIZED = optional(string, null),
  }))

  description = "*ALPHA* map of connector id --> side output targets"
  default     = {}
}


# build lookup tables to JOIN data you receive back from Worklytics with your original data.
#   - `join_key_column` should be the column you expect to JOIN on, usually 'employee_id'
#   - `columns_to_include` is an optional a list of columns to include in the lookup table,
#                       e.g. if the data you're exporting TO worklytics contains more columns than
#                       you want to have in the lookup table, you can limit to an explicit list
#   - `sanitized_accessor_names` is an optional list of GCP principals, by email with qualifier, eg:
#                       `user:alice@worklytics`, `group:analysts@worklytics.co`, or
#                        `serviceAccount:sa@worklytics.google-service-accounts.com`
variable "lookup_tables" {
  type = map(object({
    source_connector_id           = string
    join_key_column               = string
    columns_to_include            = optional(list(string))
    sanitized_accessor_principals = optional(list(string))
    expiration_days               = optional(number, 5 * 365)
    compress_output               = optional(bool)
  }))
  description = "Lookup tables to build from same source input as another connector, output to a distinct bucket. The original `join_key_column` will be preserved, "

  default = {
    #  "lookup-hris" = {
    #      source_connector_id = "hris",
    #      join_key_column = "employee_id",
    #      columns_to_include = null
    #      sanitized_accessor_principals = [
    #        # ADD LIST OF GCP PRINCIPALS HERE
    #      ],
    #  }
  }
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 2
}

variable "bucket_force_destroy" {
  type        = bool
  description = "set the `force_destroy` flag on each google_storage_bucket provisioned by this module"
  default     = false
}

variable "provision_project_level_iam" {
  description = "Whether to provision project-level IAM bindings required for Psoxy operation. Set to false if you prefer to manage these IAM bindings outside of Terraform."
  type        = bool
  default     = true
}
