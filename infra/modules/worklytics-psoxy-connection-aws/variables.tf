variable "psoxy_instance_id" {
  type        = string
  description = "friendly unique-id for Psoxy instance"
  default     = null
}

variable "psoxy_endpoint_url" {
  type        = string
  description = "url of endpoint which hosts Psoxy instance"
}

variable "display_name" {
  type        = string
  description = "display name of connector in Worklytics"
}

variable "aws_role_arn" {
  type        = string
  description = "ARN of role to assume when connecting to proxy"
}

variable "aws_region" {
  type        = string
  description = "AWS region in which proxy lambda is deployed"
}

variable "todo_step" {
  type        = number
  description = "of all todos, where does this one logically fall in sequence"
  default     = 3
}
