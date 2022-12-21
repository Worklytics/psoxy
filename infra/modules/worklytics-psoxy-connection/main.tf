
locals {
  # for backwards compatibility < 0.4.6
  instance_id = coalesce(var.psoxy_instance_id, var.display_name)
}

resource "local_file" "todo-worklytics-connection" {
  filename = "TODO ${var.todo_step} - connect ${local.instance_id} in Worklytics.md"
  content  = <<EOT
Complete the following steps in Worklytics AFTER you have deployed the Psoxy instance for your connection:
  1. Visit https://intl.worklytics.co/analytics/integrations (or login into Worklytics, and navigate to
     Manage --> Data Connections)
  2. Click on the 'Add new connection' in the upper right.
  3. Find the connector named "${var.display_name}" and click 'Connect'.
      - If presented with a further screen with several options, choose the 'via Psoxy' one.
  4. Review instructions and click 'Connect' again.
  5. Select `${var.psoxy_host_platform_id}` for "Proxy Instance Type".
  6. Copy and paste `${var.psoxy_endpoint_url}` as the value for "Psoxy Base URL".
  7. Review any additional settings that connector supports, adjusting values as you see fit, then
     click "Connect".

Worklytics will attempt some basic health checks to ensure your Psoxy instance is reachable and
configured correctly. If this fails, contact support@worklytics.co for guidance.

EOT

}

output "next_todo_step" {
  value = var.todo_step + 1
}
