
locals {
  # for backwards compatibility < 0.4.6
  instance_id = coalesce(var.proxy_instance_id, var.display_name)

  # build TODO

  worklytics_add_connection_url = "https://${var.worklytics_host}/analytics/connect/"

  # map of Worklytics setting key --> display name (matches `settings_to_provide` keys)
  autofilled_settings = {
    PROXY_AWS_ROLE_ARN  = "AWS Psoxy Role ARN",
    PROXY_AWS_REGION    = "AWS Psoxy Region"
    PROXY_ENDPOINT      = "Psoxy Base URL"
    PROXY_BUCKET_NAME   = "Bucket Name"
    parserId            = "Parser"
    GITHUB_ORGANIZATION = "GitHub Organization"
  }

  query_params = [for param_name, ux_name in local.autofilled_settings : "${param_name}=${urlencode(var.settings_to_provide[ux_name])}"
  if contains(keys(var.settings_to_provide), ux_name) && try(var.settings_to_provide[ux_name] != null, false)]
  query_param_string = join("&", local.query_params)

  # TODO try to avoid repetition of "per_setting" instructions (manual vs. deep linking)
  # use 4 whitespace indentation for sub-lists
  per_setting_instructions      = [for ux_name, value_to_provide in var.settings_to_provide : "    - Copy and paste `${value_to_provide}` as the value for \"${ux_name}\"." if !contains(values(local.autofilled_settings), ux_name)]
  per_setting_instructions_text = length(var.settings_to_provide) > 0 ? "\n${join("\n", tolist(local.per_setting_instructions))}" : ""

  per_setting_instructions_manual      = [for ux_name, value_to_provide in var.settings_to_provide : "    - Copy and paste `${value_to_provide}` as the value for \"${ux_name}\"." if value_to_provide != null]
  per_setting_instructions_manual_text = length(var.settings_to_provide) > 0 ? "\n${join("\n", tolist(local.per_setting_instructions_manual))}" : ""

  deep_link_base = "${local.worklytics_add_connection_url}${var.connector_id}/settings?PROXY_DEPLOYMENT_KIND=${var.host_platform_id}&${local.query_param_string}"

  manual_instructions = <<EOT
1. Visit https://intl.worklytics.co/analytics/integrations (or login into Worklytics, and navigate to
   Manage --> Data Connections)
2. Click on the 'Add new connection' in the upper right.
3. Find the connector named "${var.display_name}" and click 'Connect'.
    - If presented with a further screen with several options, choose the 'via Psoxy' one.
4. Review instructions and click 'Connect' again.
5. Select `${var.host_platform_id}` for "Proxy Instance Type".${local.per_setting_instructions_manual_text}
6. Review any additional settings that connector supports, adjusting values as you see fit, then
   click "Connect".
EOT


  deep_link_instructions = <<EOT
1. Ensure you're authenticated with Worklytics. Either sign-in at `https://app.worklytics.co` with
   your organization's SSO provider *or* request OTP link from your Worklytics support team.
2. Visit `${local.deep_link_base}`.${local.per_setting_instructions_text}
3. Review any additional settings that connector supports, adjusting values as you see fit, then
   click "Connect".

Alternatively, you may follow the manual instructions below:
${local.manual_instructions}
EOT

  todo_content = <<EOT
Complete the following steps in Worklytics AFTER you have deployed the Psoxy instance for your connection:

${var.connector_id == "" ? local.manual_instructions : local.deep_link_instructions}

Worklytics will attempt some basic health checks to ensure your Psoxy instance is reachable and
configured correctly. If this fails, contact support@worklytics.co for guidance.

EOT

}

resource "local_file" "todo_worklytics_connection" {
  count = var.todos_as_local_files ? 1 : 0

  filename = "TODO ${var.todo_step} - connect ${local.instance_id} in Worklytics.md"
  content  = local.todo_content
}

output "next_todo_step" {
  value = var.todo_step + 1
}

output "todo" {
  value = local.todo_content
}
