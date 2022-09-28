variable "secrets" {
  type = map(
    object({
      value       = string
      description = string
    })
  )
}
