variable "psoxy_instance_id" {
  type        = string
  description = "friendly unique-id for Psoxy instance"
  default     = null
}

variable "psoxy_host_platform_id" {
  type        = string
  description = "Psoxy host platform id (AWS, GCP, etc"
  default     = "GCP"
}

variable "psoxy_endpoint_url" {
  type        = string
  description = "url of endpoint which hosts Psoxy instance"
}

variable "display_name" {
  type        = string
  description = "display name of connector in Worklytics"
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 3
}


