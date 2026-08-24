variable "enabled_connectors" {
  type        = list(string)
  description = "ids of connectors to enable"
}

variable "base_dir" {
  type        = string
  description = "Base directory for resolving relative file paths (eg rules_file) in connector specs."
  default     = null
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
  description = "[DEPRECATED - use map instead] example MSFT user id (GUID) for test API calls; fills `{userId}` (OPTIONAL)"
  default     = "{userId}"
}

variable "msft_teams_example_team_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example MSFT Teams team id (GUID) for test API calls; fills `{teamId}` (OPTIONAL)"
  default     = "{teamId}"
}

variable "msft_teams_example_channel_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example MSFT Teams channel id (GUID) for test API calls; fills `{channelId}` (OPTIONAL)"
  default     = "{channelId}"
}

variable "msft_teams_example_chat_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example MSFT Teams chat id (GUID) for test API calls; fills `{chatId}` (OPTIONAL)"
  default     = "{chatId}"
}

variable "msft_teams_example_call_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example MSFT Teams call id (GUID) for test API calls; fills `{callId}` (OPTIONAL)"
  default     = "{callId}"
}

variable "msft_teams_example_call_record_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example MSFT Teams call-record id (GUID) for test API calls; fills `{callRecordId}` (OPTIONAL)"
  default     = "{callRecordId}"
}

variable "msft_teams_example_online_meeting_join_url" {
  type        = string
  description = "[DEPRECATED - use map instead] example Teams online-meeting join URL for test API calls; used as the JoinWebUrl filter value (OPTIONAL)"
  default     = "{joinWebUrl}"
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

variable "msft_365_connector_settings" {
  type        = map(any)
  description = "Map of configuration settings specifically for MSFT 365 connectors (e.g. test GUIDs, custom paths). Supported keys: example_msft_user_guid, example_msft_group_guid, msft_teams_example_team_guid, msft_teams_example_channel_guid, msft_teams_example_chat_guid, msft_teams_example_call_guid, msft_teams_example_call_record_guid, msft_teams_example_online_meeting_join_url, msft_onedrive_example_drive_id, msft_onedrive_example_item_id. Note that provider-controlling parameters (like msft_tenant_id or existing app IDs) remain top-level variables."
  default     = {}
}
