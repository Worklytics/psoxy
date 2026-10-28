
locals {
  test_tool_directory = "${var.path_to_tools}/psoxy-test"
}

# installs test tool into this checkout (tools/psoxy-test/node_modules)
# (no effect if no NPM, or if test tool not at expected location)
# conditional, as we don't want to depend on test tool
#
# local-exec is recorded in Terraform state. With a remote backend shared across
# git worktrees, a successful install on another checkout will skip this resource
# even when this worktree has no node_modules. examples-dev ./apply also runs
# tools/install-test-tool.sh against the current checkout to cover that.
resource "null_resource" "install_test_tool" {
  count = fileexists("${var.path_to_tools}/install-test-tool.sh") ? 1 : 0

  provisioner "local-exec" {
    command = "${var.path_to_tools}/install-test-tool.sh ${var.path_to_tools}"
  }


  triggers = {
    # trigger if path or version has changed
    path_to_psoxy_java = var.path_to_tools
    version            = var.psoxy_version

    # trigger if package.json has changed, suggesting dependencies have
    sha_package = filesha1("${local.test_tool_directory}/package.json")
  }
}

# expose whether test tool installed back, in case want to condition test commands on it
output "test_tool_installed" {
  value = fileexists("${local.test_tool_directory}/node_modules/.package-lock.json")

  depends_on = [
    null_resource.install_test_tool
  ]
}
