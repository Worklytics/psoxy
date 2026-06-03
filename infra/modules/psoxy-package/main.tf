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

  program = compact([
    "${path.module}/build.sh",
    var.skip_tests ? "-s" : "",
    var.force_bundle ? "-f" : "",
    var.path_to_psoxy_java,
    var.implementation,
  ])
}

locals {
  path_to_deployment_jar = coalesce(var.deployment_bundle, try(data.external.deployment_package[0].result.path_to_deployment_jar, "unknown"))
  filename               = var.deployment_bundle != null ? basename(var.deployment_bundle) : try(data.external.deployment_package[0].result.filename, "unknown")
  version                = var.deployment_bundle != null ? "unknown" : try(data.external.deployment_package[0].result.version, "unknown")
  built_package_hash     = try(data.external.deployment_package[0].result.deployment_package_hash, null)
}

# check blocks require Terraform >= 1.5; we support >= 1.7 (see ci-terraform-modules.yaml).
# Failed assertions surface as plan/apply warnings by default (not errors).
check "deployment_jar_exists" {
  assert {
    condition = var.deployment_bundle != null || (
      local.path_to_deployment_jar != "unknown" &&
      fileexists(local.path_to_deployment_jar)
    )
    error_message = "Deployment JAR not found at ${local.path_to_deployment_jar}. Check last-build.log in your Terraform working directory."
  }
}


output "deployment_package_hash" {
  # when `terraform console` used in directory, this output is evaluated before the build script has
  # run so the file doesn't exist yet
  value = coalesce(
    var.deployment_bundle_hash,
    local.built_package_hash != "" ? local.built_package_hash : null,
    try(filebase64sha256(local.path_to_deployment_jar), null),
    "unknown"
  )
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
