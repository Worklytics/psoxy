

# installs test tool to your machine
# (no affect if no NPM, or if test tool not at expected location)
# conditional, as we don't want to depend on test tool
resource "null_resource" "install_test_tool" {
  count = fileexists("${var.path_to_tools}/install-test-tool.sh") ? 1 : 0

  provisioner "local-exec" {
    command = "${var.path_to_tools}/install-test-tool.sh ${var.path_to_tools}"
  }

  # trigger if path or version has changed
  triggers = {
    path_to_psoxy_java = var.path_to_tools
    version            = var.psoxy_version
  }
}

# expose whether test tool installed back, in case want to condition test commands on it
output "test_tool_installed" {
  value = fileexists("${var.path_to_tools}/psoxy-test/node_modules/.package-lock.json")

  depends_on = [
    null_resource.install_test_tool
  ]
}
