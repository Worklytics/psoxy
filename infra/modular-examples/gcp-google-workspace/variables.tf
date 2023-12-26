variable "gcp_project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "environment_name" {
  type        = string
  description = "qualifier to append to name of project that will host your psoxy instance"
  default     = ""
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

variable "general_environment_variables" {
  type        = map(string)
  description = "environment variables to add for all connectors"
  default     = {}
}

variable "pseudonymize_app_ids" {
  type        = string
  description = "if set, will set value of PSEUDONYMIZE_APP_IDS environment variable to this value for all sources"
  default     = false # TODO: true in v0.5
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

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
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

variable "custom_bulk_connectors" {
  type = map(object({
    source_kind = string
    rules = object({
      pseudonymFormat       = optional(string)
      columnsToRedact       = optional(list(string))
      columnsToInclude      = optional(list(string))
      columnsToPseudonymize = optional(list(string))
      columnsToDuplicate    = optional(map(string))
      columnsToRename       = optional(map(string))
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

variable "google_workspace_example_user" {
  type        = string
  description = "User to impersonate for Google Workspace API calls (null for none)"
}

variable "google_workspace_example_admin" {
  type        = string
  description = "user to impersonate for Google Workspace API calls (null for value of `google_workspace_example_user`)"
  default     = null # will failover to user
}

variable "msft_tenant_id" {
  type        = string
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  default     = ""
}

variable "msft_owners_email" {
  type        = set(string)
  default     = []
  description = "(Only if config includes MSFT connectors). Optionally, set of emails to apply as owners on AAD apps apart from current logged user"
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

variable "github_enterprise_server_host" {
  type        = string
  default     = null
  description = "(Only required if using Github Enterprise Server connector) Host of the Github instance (ex: github.mycompany.com)."
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