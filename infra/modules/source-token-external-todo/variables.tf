
variable "source_id" {
  type        = string
  description = "The ID of the source to use for the data"
}

variable "connector_specific_external_steps" {
  type        = string
  description = "Text explaining the steps that must be completed outside Terraform for the connector; markdown."
}

variable "additional_steps" {
  type        = list(string)
  description = "Additional steps (to concatenate with the connector-specific steps to TODO file)"
  default     = []
}

variable "todo_step" {
  type        = number
  description = "[DEPRECATED - todo ordering now handled at root module level; this has no effect within the module. TODO: remove in 0.7] of all todos, where does this one logically fall in sequence"
  default     = 1
}

variable "todos_as_local_files" {
  type        = bool
  description = "[DEPRECATED - local_file resources moved to root module; this has no effect within the module. TODO: remove in 0.7] whether to render TODOs as flat files"
  default     = true
}
