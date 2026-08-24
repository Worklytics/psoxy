
variable "msft_tenant_id" {
  type        = string
  description = "ID of Microsoft tenant to connect to (req'd only if config includes MSFT connectors)"
  default     = ""
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

variable "example_msft_user_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example MSFT user guid (uuid) for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_USER_GUID}"
}

variable "msft_teams_example_team_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example of MSFT Id (GUID) of a Teams Team for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_TEAM_GUID}"
}

variable "msft_teams_example_channel_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example of MSFT Id (GUID) of a Teams Channel for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CHANNEL_GUID}"
}

variable "msft_teams_example_chat_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example of MSFT Id (GUID) of a Teams Chat for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CHAT_GUID}"
}

variable "msft_teams_example_call_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example of MSFT Id (GUID) of a Teams Call for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CALL_GUID}"
}

variable "msft_teams_example_call_record_guid" {
  type        = string
  description = "[DEPRECATED - use map instead] example of MSFT Id (GUID) of a Teams Call Record for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_CALL_RECORD_GUID}"
}

variable "msft_teams_example_online_meeting_join_url" {
  type        = string
  description = "[DEPRECATED - use map instead] example of an URL to join into an OnlineMeeting for test API calls (OPTIONAL)"
  default     = "{EXAMPLE_MSFT_TEAMS_ONLINE_MEETING_URL}"
}

variable "msft_365_connector_settings" {
  type        = map(any)
  description = "Map of configuration settings specifically for MSFT 365 connectors (e.g. test GUIDs, custom paths). Supported keys: example_msft_user_guid, example_msft_group_guid, msft_teams_example_team_guid, msft_teams_example_channel_guid, msft_teams_example_chat_guid, msft_teams_example_call_guid, msft_teams_example_call_record_guid, msft_teams_example_online_meeting_join_url, msft_onedrive_example_drive_id, msft_onedrive_example_item_id. Note that provider-controlling parameters (like msft_tenant_id or existing app IDs) remain top-level variables."
  default     = {}

  validation {
    condition = length(setsubtract(
      toset(keys(var.msft_365_connector_settings)),
      toset([
        "example_msft_user_guid",
        "example_msft_group_guid",
        "msft_teams_example_team_guid",
        "msft_teams_example_channel_guid",
        "msft_teams_example_chat_guid",
        "msft_teams_example_call_guid",
        "msft_teams_example_call_record_guid",
        "msft_teams_example_online_meeting_join_url",
        "msft_onedrive_example_drive_id",
        "msft_onedrive_example_item_id",
      ])
    )) == 0

    error_message = "msft_365_connector_settings contains unsupported keys. Supported keys are: example_msft_user_guid, example_msft_group_guid, msft_teams_example_team_guid, msft_teams_example_channel_guid, msft_teams_example_chat_guid, msft_teams_example_call_guid, msft_teams_example_call_record_guid, msft_teams_example_online_meeting_join_url, msft_onedrive_example_drive_id, and msft_onedrive_example_item_id."
  }
}
