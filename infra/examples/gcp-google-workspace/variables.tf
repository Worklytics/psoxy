variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance; must exist"
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
  description = "qualifier to append to name of project that will host your psoxy instance"
  default     = ""
}

variable "gcp_org_id" {
  type        = string
  description = "DEPRECATED; IGNORED; your GCP organization ID"
  default     = null
}

variable "gcp_folder_id" {
  type        = string
  description = "DEPRECATED; IGNORED; optionally, a folder into which to provision it"
  default     = null
}

variable "gcp_billing_account_id" {
  type        = string
  description = "DEPRECATED; IGNORED; billing account ID; needed to create the project"
  default     = null
}


variable "worklytics_sa_emails" {
  type        = list(string)
  description = "service accounts for your organization's Worklytics instances (list supported for test/dev scenarios)"
}

variable "connector_display_name_suffix" {
  type        = string
  description = "suffix to append to display_names of connector SAs; helpful to distinguish between various ones in testing/dev scenarios"
  default     = ""
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

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists for this proxy version"
  default     = false
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

variable "gcp_region" {
  type        = string
  description = "Region in which to provision GCP resources, if applicable"
  default     = "us-central1"
}

variable "replica_regions" {
  type        = list(string)
  description = "List of regions in which to replicate secrets."
  default = [
    "us-central1",
    "us-west1",
  ]
}

variable "enabled_connectors" {
  type        = list(string)
  description = "list of ids of connectors to enabled; see modules/worklytics-connector-specs"

  default = [
    "asana",
    "gdirectory",
    "gcal",
    "gmail",
    "gdrive",
    "google-chat",
    "google-meet",
    "hris",
    "slack-discovery-api",
    "zoom",
  ]
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

variable "custom_bulk_connectors" {
  type = map(object({
    source_kind = string
    rules = object({
      pseudonymFormat       = optional(string, "URL_SAFE_TOKEN")
      columnsToRedact       = optional(list(string)) # columns to remove from CSV
      columnsToInclude      = optional(list(string)) # if you prefer to include only an explicit list of columns, rather than redacting those you don't want
      columnsToPseudonymize = optional(list(string)) # columns to pseudonymize
      columnsToDuplicate    = optional(map(string))  # columns to create copy of; name --> new name
      columnsToRename       = optional(map(string))  # columns to rename: original name --> new name; renames applied BEFORE pseudonymization
    })
    settings_to_provide = optional(map(string), {})
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

variable "non_production_connectors" {
  type        = list(string)
  description = "connector ids in this list will be in development mode (not for production use"
  default     = []
}

variable "google_workspace_example_user" {
  type        = string
  description = "User to impersonate for Google Workspace API calls (null for none)"
  default     = null
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}

variable "salesforce_domain" {
  type        = string
  description = "Domain of the Salesforce to connect to (only required if using Salesforce connector). To find your My Domain URL, from Setup, in the Quick Find box, enter My Domain, and then select My Domain"
  default     = ""
}

variable "jira_server_url" {
  type        = string
  default     = null
  description = "(Only required if using Jira Server connector) URL of the Jira server (ex: myjiraserver.mycompany.com)"
}

variable "jira_cloud_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Cloud connector) Cloud id of the Jira Cloud to connect to (ex: 1324a887-45db-1bf4-1e99-ef0ff456d421)."
}

variable "example_jira_issue_id" {
  type        = string
  default     = null
  description = "(Only required if using Jira Server/Cloud connector) Id of an issue for only to be used as part of example calls for Jira (ex: ETV-12)"
}

variable "github_installation_id" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) InstallationId of the application in your org for authentication with the proxy instance (ex: 123456)"
}

variable "github_organization" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) Name of the organization to be used as part of example calls for Github (ex: Worklytics)"
}

variable "github_example_repository" {
  type        = string
  default     = null
  description = "(Only required if using Github connector) Name for the repository to be used as part of example calls for Github (ex: psoxy)"
}

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one). Can be GCS url, eg 'gs://artifacts-bucket/psoxy-0.4.28.zip'."
  default     = null

  validation {
    condition     = var.deployment_bundle == null || var.deployment_bundle != ""
    error_message = "`deployment_bundle`, if non-null, must be non-empty string."
  }
}

locals {
  # tflint-ignore: terraform_unused_declarations
  validate_salesforce_domain         = (var.salesforce_domain == null || var.salesforce_domain == "" || can(regex(":|\\/", try(var.salesforce_domain, "")))) && contains(var.enabled_connectors, "salesforce")
  validate_salesforce_domain_message = "The salesforce_domain var should be populated and to be with only the domain (without protocol or query paths) itself if enabled."
  validate_salesforce_domain_check = regex(
    "^${local.validate_salesforce_domain_message}$",
    (!local.validate_salesforce_domain
      ? local.validate_salesforce_domain_message
  : ""))
}