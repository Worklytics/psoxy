variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "gcp_terraform_sa_account_email" {
  type        = string
  description = "Email of GCP service account that will be used to provision GCP resources. Leave 'null' to use application default for you environment."
  default     = null

  validation {
    condition     = var.gcp_terraform_sa_account_email == null || can(regex(".*@.*\\.iam\\.gserviceaccount\\.com$", var.gcp_terraform_sa_account_email))
    error_message = "The gcp_terraform_sa_account_email value should be a valid GCP service account email address."
  }
}

variable "environment_name" {
  type        = string
  description = "Qualifier to append to names/ids of resources for psoxy. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = ""

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_name))
    error_message = "The environment_id must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "config_parameter_prefix" {
  type        = string
  description = "A prefix to give to all config parameters (GCP Secret Manager Secrets) created/consumed by this module. If omitted, and `environment_id` provided, that will be used."
  default     = ""
}

variable "default_labels" {
  type        = map(string)
  description = "Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}

  validation {
    condition     = alltrue([for k, v in var.default_labels : can(regex("^[a-z][a-z0-9-_]{0,62}$", k))])
    error_message = "GCP label keys must start with a lowercase letter, can contain lowercase letters, numbers, underscores and dashes only and must be no longer than 63 characters."
  }

  validation {
    condition     = alltrue([for k, v in var.default_labels : can(regex("^[a-z0-9-_]{0,63}$", v))])
    error_message = "GCP label values must contain only lowercase letters, numbers, underscores and dashes only and be no longer than 63 characters."
  }

  validation {
    condition     = length(var.default_labels) <= 64
    error_message = "GCP resources cannot have more than 64 labels."
  }
}

variable "worklytics_host" {
  type        = string
  description = "host of worklytics instance where tenant resides. (e.g. intl.worklytics.co for prod; but may differ for dev/staging)"
  default     = "intl.worklytics.co"
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
  description = "path to deployment bundle to use (if not provided, will build one). Can be GCS url, eg 'gs://psoxy-public-artifacts/psoxy-0.4.28.zip'."
  default     = null

  validation {
    condition     = var.deployment_bundle == null || var.deployment_bundle != ""
    error_message = "`deployment_bundle`, if non-null, must be non-empty string."
  }
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

variable "general_environment_variables" {
  type        = map(string)
  description = "environment variables to add for all connectors"
  default     = {}

  validation {
    condition     = !contains(keys(var.general_environment_variables), "IS_DEVELOPMENT_MODE")
    error_message = "Cannot pass IS_DEVELOPMENT_MODE as a general environment variable; add connector id to `non_production_connectors` instead."
  }

  validation {
    condition     = !contains(keys(var.general_environment_variables), "EMAIL_CANONICALIZATION")
    error_message = "Use `email_canonicalization` instead of passing EMAIL_CANONICALIZATION as a general environment variable."
  }
}

variable "pseudonymize_app_ids" {
  type        = string
  description = "if set, will set value of PSEUDONYMIZE_APP_IDS environment variable to this value for all sources"
  default     = true
}

variable "email_canonicalization" {
  type        = string
  description = "defines how email address are processed prior to hashing, hence which are considered 'canonically equivalent'; one of 'STRICT' (default and most standard compliant) or 'IGNORE_DOTS' (probably most in line with user expectations)"
  default     = "IGNORE_DOTS"

  validation {
    condition     = var.email_canonicalization == "STRICT" || var.email_canonicalization == "IGNORE_DOTS"
    error_message = "`email_canonicalization` must be one of 'STRICT' or 'IGNORE_DOTS'."
  }
}

variable "gcp_region" {
  type        = string
  description = "Region in which to provision GCP resources, if applicable"
  default     = "us-central1"
}

variable "vpc_config" {
  type = object({
    network              = string           # Local name of the VPC network resource on which to provision the VPC connector (required if `serverless_connector` is not provided)
    subnet               = string           # Local name of the VPC subnet resource on which to provision the VPC connector (required if `serverless_connector` is not provided). NOTE: Subnet MUST have /28 netmask (required by Google Cloud for VPC connectors)
    serverless_connector = optional(string) # Format: projects/{project}/locations/{location}/connectors/{connector}
  })

  description = "**alpha** configuration of a VPC to be used by the Psoxy instances, if any (null for none)."
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
    error_message = "vpc_config.network must be lowercase letters, numbers, or dashes."
  }

  validation {
    condition = (
      var.vpc_config == null
      || try(var.vpc_config.serverless_connector, null) != null
      || (try(var.vpc_config.network, null) != null && try(var.vpc_config.subnet, null) != null)
    )
    error_message = "If vpc_config is provided without serverless_connector, both network and subnet are required."
  }
}



variable "secret_replica_locations" {
  type        = list(string)
  description = "List of locations to which to replicate GCP Secret Manager secrets. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1",
  ]

  validation {
    condition     = length(var.secret_replica_locations) > 0
    error_message = "`gcp_secret_replica_locations` must be non-empty list."
  }
}

variable "kms_key_ring" {
  type        = string
  description = "name of KMS key ring on which to provision any required KMS keys; if omitted, one will be created for you"
  default     = null
}

variable "custom_artifacts_bucket_name" {
  type        = string
  description = "name of bucket to use for custom artifacts, if you want something other than default. Ignored if you pass gcs url for `deployment_bundle`."
  default     = null
}

variable "enabled_connectors" {
  type        = list(string)
  description = "list of ids of connectors to enabled; see modules/worklytics-connector-specs"
}

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
}

variable "bulk_input_expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire"
  default     = 30
}

variable "bulk_sanitized_expiration_days" {
  type        = number
  description = "Number of days after which objects in the bucket will expire. This should match the amount of historical data you wish for Worklytics to analyze (eg, typically multiple years)."
  default     = 1805 # 5 years; intent is 'forever', but some upperbound in case bucket is forgotten
}

variable "custom_api_connectors" {
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

  description = "map of custom API connectors to provision"
  default     = {}
}

variable "custom_api_connector_rules" {
  type        = map(string)
  description = "map of connector id --> YAML file with custom rules"
  default     = {}
}

variable "webhook_collectors" {
  type = map(object({
    worklytics_connector_id   = optional(string, "work-data-generic-psoxy")
    worklytics_connector_name = optional(string, "Workplace Metadata via Psoxy")
    display_name              = optional(string, "Webhooks Collected via Psoxy")
    source_kind               = optional(string, "work-event") # source kind for this webhook collector, used for labeling and categorization
    rules_file                = string
    provision_auth_key = optional(object({                           # whether to provision auth keys for webhook collector; if not provided, will not provision any
      rotation_days = optional(number, null)                         # null means no rotation; if > 0, will rotate every N days
      key_spec      = optional(string, "RSA_SIGN_PKCS1_2048_SHA256") # see https://cloud.google.com/kms/docs/reference/rest/v1/CryptoKeyVersionAlgorithm
    }), null)
    auth_public_keys     = optional(list(string), [])    # list of public keys to use for verifying webhook signatures; if empty AND no auth keys provision, no app-level auth will be done
    allow_origins        = optional(list(string), ["*"]) # list of origins to allow for CORS, eg 'https://my-app.com'; if you want to allow all origins, use ['*'] (the default)
    output_path_prefix   = optional(string, "")          # optional path prefix to prepend to webhook output files in bucket (e.g., 'events_', 'webhooks/')
    example_payload_file = optional(string, null)        # path to example payload file to use for testing; if provided, will be used in the test script
    example_identity     = optional(string, null)        # example identity to use for testing; if provided, will be used to test the collector
  }))

  default = {}

  description = "map of webhook collector id --> webhook collector configuration"
}

variable "custom_bulk_connectors" {
  type = map(object({
    source_kind               = string
    display_name              = optional(string, "Custom Bulk Connector")
    input_bucket_name         = optional(string) # allow override of default bucket name
    sanitized_bucket_name     = optional(string) # allow override of default bucket name
    worklytics_connector_id   = optional(string, "bulk-import-psoxy")
    worklytics_connector_name = optional(string, "Custom Bulk Data via Psoxy")
    rules = optional(object({
      pseudonymFormat                = optional(string, "URL_SAFE_TOKEN")
      columnsToRedact                = optional(list(string)) # columns to remove from CSV
      columnsToInclude               = optional(list(string)) # if you prefer to include only an explicit list of columns, rather than redacting those you don't want
      columnsToPseudonymize          = optional(list(string)) # columns to pseudonymize
      columnsToPseudonymizeIfPresent = optional(list(string))
      columnsToDuplicate             = optional(map(string)) # columns to create copy of; name --> new name
      columnsToRename                = optional(map(string)) # columns to rename: original name --> new name; renames applied BEFORE pseudonymization
      fieldsToTransform = optional(map(object({
        newName    = string
        transforms = optional(list(map(string)), [])
      })))
    }))
    available_memory_mb = optional(number)
    timeout_seconds     = optional(number)
    rules_file          = optional(string)
    settings_to_provide = optional(map(string), {})
    example_file        = optional(string)
  }))
  description = "specs of custom bulk connectors to create"

  default = {
    #    "custom-survey" = {
    #      source_kind = "survey"
    #      rules       = {
    #        columnsToRedact       = []
    #        columnsToPseudonymize = [
    #          "employee_id", # primary key
    #          # "employee_email", # if exists
    #        ]
    #      }
    #    }
  }
}

variable "custom_bulk_connector_rules" {
  type = map(object({
    pseudonymFormat                = optional(string, "URL_SAFE_TOKEN")
    columnsToRedact                = optional(list(string), []) # columns to remove from CSV
    columnsToInclude               = optional(list(string))     # if you prefer to include only an explicit list of columns, rather than redacting those you don't want
    columnsToPseudonymize          = optional(list(string), []) # columns to pseudonymize
    columnsToPseudonymizeIfPresent = optional(list(string))
    columnsToDuplicate             = optional(map(string)) # columns to create copy of; name --> new name
    columnsToRename                = optional(map(string)) # columns to rename: original name --> new name; renames applied BEFORE pseudonymization
    fieldsToTransform = optional(map(object({
      newName    = string
      transforms = optional(list(map(string)), [])
    })))
  }))

  description = "map of connector id --> rules object"
  default = {
    # hris = {
    #   columnsToRedact       = []
    #   columnsToPseudonymize = [
    #     "EMPLOYEE_ID",
    #     "EMPLOYEE_EMAIL",
    #     "MANAGER_ID",
    #     "MANAGER_EMAIL"
    #  ]
    # columnsToRename = {
    #   # original --> new
    #   "workday_id" = "employee_id"
    # }
    # columnsToInclude = [
    # ]
  }
}

variable "custom_bulk_connector_arguments" {
  type = map(object({
    available_memory_mb = optional(number)
    timeout_seconds     = optional(number)
  }))

  description = "map of connector id --> arguments object, to override defaults for bulk connector instances"
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
    expiration_days               = optional(number)
    output_bucket_name            = optional(string) # allow override of default bucket name
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

variable "todos_as_outputs" {
  type        = bool
  description = "whether to render TODOs as outputs (useful if you're using Terraform Cloud/Enterprise, or somewhere else where the filesystem is not readily accessible to you)"
  default     = false
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "bucket_force_destroy" {
  type        = bool
  description = "set the `force_destroy` flag on each google_storage_bucket provisioned by this configuration"
  default     = false
}

variable "provision_project_level_iam" {
  description = "Whether to provision project-level IAM bindings required for Psoxy operation. Set to false if you prefer to manage these IAM bindings outside of Terraform."
  type        = bool
  default     = true
}
