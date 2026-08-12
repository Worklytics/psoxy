variable "gcp_project_id" {
  type        = string
  description = "GCP project ID that hosts the ALB and API connectors."
}

variable "gcp_region" {
  type        = string
  description = "Region of the API connector Cloud Run / Cloud Functions services (for serverless NEGs)."
}

variable "environment_id_prefix" {
  type        = string
  description = "Prefix for ALB resource names (matches gcp-host environment_id_prefix)."
  default     = ""
}

variable "global_address_id" {
  type        = string
  description = "ID of the reserved google_compute_global_address for the HTTPS forwarding rule (created by the caller to avoid module cycles with connector endpoint URLs)."
}

variable "global_address_ip" {
  type        = string
  description = "IP address of the reserved global address (used as self-signed cert SAN when domain is null)."
}

variable "api_connector_function_names" {
  type        = map(string)
  description = "Map of connector instance id => Cloud Run / Cloud Functions service name (environment_id_prefix + instance id)."
}

variable "domain" {
  type        = string
  description = "Optional. Google-managed HTTPS when set; self-signed HTTPS on the reserved global IP when null (PoC only)."
  default     = null
  nullable    = true
}

variable "allowed_data_access_ip_blocks" {
  type        = list(string)
  description = "Optional Cloud Armor allowlist. Null = no security policy (all source IPs allowed through the LB). Non-empty = allow those CIDRs + default deny. Empty list is invalid (reject at caller)."
  default     = null
  nullable    = true

  validation {
    condition     = var.allowed_data_access_ip_blocks == null || try(length(var.allowed_data_access_ip_blocks) > 0, false)
    error_message = "allowed_data_access_ip_blocks must be null (allow all) or a non-empty list; an empty list is invalid."
  }
}
