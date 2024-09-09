variable "enabled_connectors" {
  type        = list(string)
  description = "ids of connectors to enable"
}

variable "environment_id" {
  type        = string
  description = "Qualifier to append to names/ids of resources. If not empty, A-Za-z0-9 or - characters only. Max length 10. Useful to distinguish between deployments into same GCP project."
  default     = "psoxy"

  validation {
    condition     = can(regex("^[A-z0-9\\-]{0,20}$", var.environment_id))
    error_message = "The environment_name must be 0-20 chars of [A-z0-9\\-] only."
  }
}

variable "msft_tenant_id" {
  type        = string
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  default     = ""
}

variable "example_msft_user_guid" {
  type        = string
  description = "example MSFT user guid (uuid) for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_USER_GUID}"
}

variable "msft_teams_example_team_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Team for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_TEAM_GUID}"
}

variable "msft_teams_example_channel_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Channel for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CHANNEL_GUID}"
}

variable "msft_teams_example_chat_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Chat for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CHAT_GUID}"
}

variable "msft_teams_example_call_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Call for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CALL_GUID}"
}

variable "msft_teams_example_call_record_guid" {
  type        = string
  description = "example of MSFT Id (GUID) of a Teams Call Record for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CALL_RECORD_GUID}"
}

variable "msft_teams_example_online_meeting_join_url" {
  type        = string
  description = "example of an URL to join into an OnlineMeeting for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_ONLINE_MEETING_URL}"
}

variable "msft_owners_email" {
  type        = set(string)
  description = "(Only if config includes MSFT connectors). Optionally, set of emails to apply as owners on AAD apps apart from current logged user. Ignored if `existing_app_object_id` provided."
  default     = []
}

variable "msft_connector_app_object_id" {
  type        = string
  description = "BETA; if provided, the app corresponding to this object id will be used instead of creating new ones per source. User must ensure that roles/scopes are appropriate for the connector"
  default     = null
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}