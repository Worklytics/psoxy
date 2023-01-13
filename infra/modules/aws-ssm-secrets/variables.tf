variable "secrets" {
  type = map(
    object({
      value       = string
      description = string
    })
  )
}

variable "path" {
  type        = string
  description = "secrets will be created under this path (prefix); be sure to include trailing `/` if needed."
  default     = ""
}
