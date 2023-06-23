
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
  description = "of all todos, where does this one logically fall in sequence"
  default     = 1
}

variable "todos_as_local_files" {
  type        = bool
  description = "whether to render TODOs as flat files"
  default     = true
}
