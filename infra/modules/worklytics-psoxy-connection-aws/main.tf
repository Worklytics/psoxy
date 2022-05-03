


resource "local_file" "todo-worklytics-connection" {
  filename = "TODO - connect ${var.display_name} in Worklytics .md"
  content  = <<EOT
Complete the following steps in Worklytics AFTER you have deployed the Psoxy instance for your connection:
  1.  Visit https://intl.worklytics.co/#integrations (or login into Worklytics, and navigate to
      Manage --> Data Connections)
  2.  Find the connector named "${var.display_name}", click "Add new connection"
  3.  Copy and paste `${var.psoxy_endpoint_url}/` as the value for "Psoxy Base URL".
  4.  Copy and paste `${var.aws_role_arn}` as the value for "AWS Psoxy Role ARN".
  5.  Copy and paste `${var.aws_region}` as the value for "AWS Psoxy Region".
  6.  Review any additional settings that connector supports, adjusting values as you see fit, then
      click "Connect".

Worklytics will attempt some basic health checks to ensure your Psoxy instance is reachable and
configured correctly. If this fails, contact support@worklytics.co for guidance.

EOT

}
