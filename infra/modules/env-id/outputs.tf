
output "id" {
  value       = local.id
  description = "The id value, generated deterministically for the inputs, according to restrictions specified via variables"
}

output "path_prefix" {
  value = length(local.id) > 0 ? "${local.id}/" : ""
}
