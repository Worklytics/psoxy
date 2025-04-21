# packages psoxy for deployment

# TODO: solve weirdness with AWS case: AWS lambda is deployed directly by Terraform from a code
# package built by terraform, but terraform doesn't understand this dependency in its plan. we make
# it work via explicit 'depends_on', but Terraform still gives 'error inconsistent plan' as its
# original plan presumed the sha-256 of the package

# build psoxy bundle with external build script, via a 'data' resource.
# NOTE: since invoked via 'data', happens during plan, rather than 'apply'; this is incorrect
# semantics if you view the package as a 'resource', but is reasonable if you view it as just a
# configuration value (eg, the content of a lambda function)
# NOTE: pass deployment jar path through the script simply to make outputs of this Terraform module
# dependent on the build script actually running, avoiding Terraform thinking the outputs are
# known prior to executing the build

data "external" "deployment_package" {
  count = var.deployment_bundle == null ? 1 : 0

  program = [
    "${path.module}/build.sh",
    var.skip_tests ? "-s" : "",
    var.force_bundle ? "-f" : "",
    var.path_to_psoxy_java,
    var.implementation,
  ]
}

locals {
  path_to_deployment_jar = coalesce(var.deployment_bundle, try(data.external.deployment_package[0].result.path_to_deployment_jar, "unknown"))
  filename               = coalesce(var.deployment_bundle, try(data.external.deployment_package[0].result.filename, "unknown"))
  version                = coalesce(var.deployment_bundle, try(data.external.deployment_package[0].result.version, "unknown"))
}


output "deployment_package_hash" {
  # when `terraform console` used in directory, this output is evaluated before the build script has
  # run so the file doesn't exist yet
  value = try(filebase64sha256(local.path_to_deployment_jar), "unknown")
}

output "path_to_deployment_jar" {
  value = local.path_to_deployment_jar
}

output "filename" {
  value = local.filename
}

output "version" {
  value = local.version
}
