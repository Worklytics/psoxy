# a webhook-collecting mode instance implemented in AWS

# provision a Psoxy instance into AWS account

terraform {
  required_providers {
    aws = {
      version = ">= 4.12, < 5.0"
    }
  }
}


# NOTE: region used to be passed in as a variable; put it MUST match the region in which the lambda
# is provisioned, and that's implicit in the provider - so we should just infer from the provider
data "aws_region" "current" {}

locals {
  # from v0.5, these will be required; for now, allow `null` but filter out so taken from config yaml
  required_env_vars = { for k, v in {
    # queue
    # output bucket
    }
    : k => v if v != null
  }

  http_methods = var.http_methods # Use http_methods directly without adding OPTIONS

  auth_issuer = "${var.api_gateway_v2.stage_invoke_url}/${module.gate_instance.function_name}"

  collection_path = "/collect"
}

module "env_id" {
  source = "../env-id"

  environment_name          = var.environment_name
  supported_word_delimiters = ["-"]
  preferred_word_delimiter  = "-"
}


module "sanitized_output" {
  source = "../aws-output-s3"

  environment_name = var.environment_name
  instance_id      = var.instance_id
  bucket_suffix    = "sanitized-webhooks"
}

resource "aws_sqs_queue" "sanitized_webhooks_to_batch" {
  name = "${var.instance_id}-sanitized-webhooks-to-batch"

  visibility_timeout_seconds = 90 # must be >= timeout of the lambda itself
}

module "rules_parameter" {
  source    = "../aws-ssm-rules"
  file_path = var.rules_file
  prefix    = var.path_to_instance_ssm_parameters
}

# q: better name for this module invocation ?
# it's an instance of a gate/gateway, implemented as a lambda function
# and in this particular case, it's inverted in a sense, as data goes INTO it, rather than OUT (via API requests)
module "gate_instance" {
  source = "../aws-psoxy-lambda"

  environment_name                     = var.environment_name
  instance_id                          = var.instance_id
  handler_class                        = var.handler_class
  path_to_function_zip                 = var.path_to_function_zip
  function_zip_hash                    = var.function_zip_hash
  function_env_kms_key_arn             = var.function_env_kms_key_arn
  logs_kms_key_arn                     = var.logs_kms_key_arn
  memory_size_mb                       = var.memory_size_mb
  timeout_seconds                      = 55
  reserved_concurrent_executions       = var.reserved_concurrent_executions
  path_to_instance_ssm_parameters      = var.path_to_instance_ssm_parameters
  path_to_shared_ssm_parameters        = var.path_to_shared_ssm_parameters
  global_parameter_arns                = var.global_parameter_arns
  global_secrets_manager_secret_arns   = var.global_secrets_manager_secret_arns
  ssm_kms_key_ids                      = var.ssm_kms_key_ids
  log_retention_in_days                = var.log_retention_days
  vpc_config                           = var.vpc_config
  secrets_store_implementation         = var.secrets_store_implementation
  aws_lambda_execution_role_policy_arn = var.aws_lambda_execution_role_policy_arn
  iam_roles_permissions_boundary       = var.iam_roles_permissions_boundary
  sqs_trigger_queue_arns = [
    aws_sqs_queue.sanitized_webhooks_to_batch.arn
  ]
  sqs_send_queue_arns = [
    aws_sqs_queue.sanitized_webhooks_to_batch.arn
  ]
  s3_outputs = [
    "s3://${module.sanitized_output.bucket_id}/"
  ]
  aws_kms_public_keys = concat([
    for k in var.webhook_auth_public_keys : k if startswith(k, "aws-kms:")
    ],
    aws_kms_key.auth_key[*].arn
  )

  environment_variables = merge(
    var.environment_variables,
    local.required_env_vars,
    {
      WEBHOOK_OUTPUT               = aws_sqs_queue.sanitized_webhooks_to_batch.url
      WEBHOOK_BATCH_OUTPUT         = "s3://${module.sanitized_output.bucket_id}/${var.output_path_prefix}"
      REQUIRE_AUTHORIZATION_HEADER = length(local.accepted_auth_keys) > 0
      ALLOW_ORIGINS                = join(",", var.allow_origins)
      CUSTOM_RULES_SHA             = module.rules_parameter.rules_hash
    },
    length(local.accepted_auth_keys) > 0 ? {
      AUTH_ISSUER        = local.auth_issuer
      ACCEPTED_AUTH_KEYS = join(",", local.accepted_auth_keys)
    } : {}
  )
}


resource "aws_apigatewayv2_integration" "map" {
  api_id                 = var.api_gateway_v2.id
  integration_type       = "AWS_PROXY"
  connection_type        = "INTERNET"
  integration_method     = "POST"
  integration_uri        = module.gate_instance.function_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000 # ideally would be 55 or 60, but docs say limit is 30s
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  name             = "jwt-authorizer"
  api_id           = var.api_gateway_v2.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]


  jwt_configuration {
    issuer = local.auth_issuer
    audience = [
      local.auth_issuer
    ]
  }

  depends_on = [
    aws_apigatewayv2_integration.map,
    aws_apigatewayv2_route.well_known # potential circular dependency here? seems that in order to provision the authorizer, AWS checks that issuer responds at .well-known
  ]
}

# serve JWKS from /{instance-id}/.well-known/jwks.json
# q: will this work? circular, as the API is serving the JWKS that will be used to authenticate requests to other routes
# this MUST be stood-up before the other route, which rely on it as the issuer for the JWT authorizer
resource "aws_apigatewayv2_route" "well_known" {
  api_id             = var.api_gateway_v2.id
  route_key          = "GET /${module.gate_instance.function_name}/.well-known/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.map.id}"
  authorization_type = "NONE"
}


// q: change this route to /collect instead of /{proxy+}?  any need for {proxy+} here?
resource "aws_apigatewayv2_route" "collect" {
  for_each = toset(local.http_methods) # q: should we just limit this to POST??

  api_id             = var.api_gateway_v2.id
  route_key          = "${each.key} /${module.gate_instance.function_name}${local.collection_path}"
  target             = "integrations/${aws_apigatewayv2_integration.map.id}"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  authorization_type = "JWT"
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "Allow${module.gate_instance.function_name}Invoke"
  action        = "lambda:InvokeFunction"
  function_name = module.gate_instance.function_name
  principal     = "apigateway.amazonaws.com"

  # The /*/*/ part allows invocation from any stage, method and resource path
  # within API Gateway REST API.
  # TODO: limit by http method here too?  for webhooks, would need POST, OPTIONS at min
  source_arn = "${var.api_gateway_v2.execution_arn}/*/*/${module.gate_instance.function_name}/*"
}


resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn                   = aws_sqs_queue.sanitized_webhooks_to_batch.arn
  function_name                      = module.gate_instance.function_name
  enabled                            = true
  batch_size                         = 100 # eg, merge up to 100 webhooks into a single batch (object in the S3 output)
  maximum_batching_window_in_seconds = 60  # max time to wait before combining whatever we have; could be up to 300s, but for testing let's start with 60s

  depends_on = [
    module.gate_instance
  ]
}

resource "aws_iam_policy" "sanitized_bucket_read" {
  name        = "${module.env_id.id}_BucketRead_${module.sanitized_output.bucket_id}"
  description = "Allow to read content from bucket: ${module.sanitized_output.bucket_id}"

  policy = jsonencode(
    {
      "Version" : "2012-10-17",
      "Statement" : [
        {
          "Action" : [
            "s3:GetObject",
            "s3:ListBucket"
          ],
          "Effect" : "Allow",
          "Resource" : [
            "${module.sanitized_output.bucket_arn}",
            "${module.sanitized_output.bucket_arn}/*"
          ]
        }
      ]
  })

  lifecycle {
    ignore_changes = [
      tags
    ]
  }
}

resource "aws_iam_role_policy_attachment" "reader_policy_to_accessor_role" {
  for_each = toset([for r in var.sanitized_accessor_role_names : r if r != null])

  role       = each.key
  policy_arn = aws_iam_policy.sanitized_bucket_read.arn
}

## Authentication Key Pair Provisioning, if any

# if rotation_days set, then we'll provision TWO keys, rotating one after N/2 days, and the other after N days
# set BOTH keys into list that the lambda considers valid

locals {
  keys_to_provision = var.provision_auth_key == null ? 0 : (var.provision_auth_key.rotation_days == null ? 1 : 2)
}

resource "time_rotating" "auth_key_rotation" {
  count = local.keys_to_provision > 1 ? local.keys_to_provision : 0

  rotation_days = (count.index + 1) * floor(var.provision_auth_key.rotation_days / local.keys_to_provision)
}

# provision two keys always, and rotate after N/2, and N days
resource "aws_kms_key" "auth_key" {
  count = local.keys_to_provision

  description              = "${var.instance_id} authentication key pair ${try(time_rotating.auth_key_rotation[count.index].rotation_rfc3339, "")}"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = var.provision_auth_key.key_spec
  multi_region             = false

  tags = local.keys_to_provision == 1 ? {} : {
    rotation_time = time_rotating.auth_key_rotation[count.index].rotation_rfc3339
  }
}


locals {
  accepted_auth_keys = concat(
    var.webhook_auth_public_keys,
    var.provision_auth_key == null ? [] : [for arn in aws_kms_key.auth_key[*].arn : "aws-kms:${arn}"]
  )

  # these ARE sorted, bc maps in tf are always iterated in lexicographic order of the keys
  auth_key_arns_sorted = values({
    for k in aws_kms_key.auth_key[*] : try(k.tags.rotation_time, k.arn) => k.arn
  })
  auth_key_ids_sorted = values({
    for k in aws_kms_key.auth_key[*] : try(k.tags.rotation_time, k.arn) => k.key_id
  })
  allow_test_role_to_sign = var.test_caller_role_arn != null && local.keys_to_provision > 0
}

resource "aws_kms_alias" "auth_key_alias" {
  count = local.keys_to_provision > 0 ? 1 : 0

  name = "alias/${module.env_id.path_prefix}${var.instance_id}_signing-key"

  # TODO: from terraform v1.11, can simply pass -1 to `element` to get the last element
  target_key_id = element(local.auth_key_ids_sorted, length(local.auth_key_arns_sorted) - 1) # should be latest, bc sorted by rotation_time
}

## end Authentication Key Pair Provisioning


locals {
  proxy_endpoint_url = "${var.api_gateway_v2.stage_invoke_url}/${module.gate_instance.function_name}"

  # don't want to *require* assumption of a role for testing; while we expect it in usual case
  # (a provisioner must assume PsoxyCaller role for the test), customer could be using a single
  # admin user for everything such that it's not required
  role_param = var.test_caller_role_arn == null ? "" : " -r \"${var.test_caller_role_arn}\""

  command_npm_install = "npm --prefix ${var.path_to_repo_root}tools/psoxy-test install"
  command_cli_call    = "node ${var.path_to_repo_root}tools/psoxy-test/cli-call.js ${local.role_param} --region \"${data.aws_region.current.id}\""
  command_test_logs   = "node ${var.path_to_repo_root}tools/psoxy-test/cli-logs.js ${local.role_param} --region \"${data.aws_region.current.id}\" -l \"${module.gate_instance.log_group}\""

  todo_content = <<EOT

## Testing ${var.instance_id}

Review the deployed function in AWS console:

- https://console.aws.amazon.com/lambda/home?region=${data.aws_region.current.id}#/functions/${module.gate_instance.function_name}?tab=monitoring

We provide some Node.js scripts to simplify testing your proxy deployment. To be able run test
commands below, you will need
   - Node.js (>=20) and npm (v >=8) installed.
   - install the tool itself (in the location from which you plan to run the test commands, if it's
     not the same location where you originally ran the Terraform apply)

```shell
${local.command_npm_install}
```

### Make "test calls"
First, run an initial "Health Check" call to make sure the Psoxy instance is up and running:

```shell
${local.command_cli_call} -u ${local.proxy_endpoint_url}/ --health-check
```

Then, based on your configuration, these are some example test calls you can try (YMMV):

```shell
${local.command_cli_call} -u ${local.proxy_endpoint_url} --body '${coalesce(var.example_payload, "{\"test\": \"body\"}")}' --identity '${var.example_identity}'
```
### Check logs (AWS CloudWatch)

Based on your configuration, the following command allows you to inspect the
logs of your Psoxy deployment:

```shell
${local.command_test_logs}
```

---

Please, check the documentation of our [Psoxy Testing tools](${var.path_to_repo_root}tools/psoxy-test/README.md)
for a detailed description of all the different options.

Contact support@worklytics.co for assistance modifying the rules as needed.

EOT
}

locals {
  test_script = templatefile("${path.module}/test_script.tftpl", {
    collector_endpoint_url = local.proxy_endpoint_url,
    function_name          = module.gate_instance.function_name,
    command_cli_call       = local.command_cli_call,
    signing_key_arn        = local.keys_to_provision > 0 ? element(local.auth_key_arns_sorted, length(local.auth_key_arns_sorted) - 1) : null,
    example_payload        = coalesce(var.example_payload, "{\"test\": \"data\"}")
    example_identity       = var.example_identity
    collection_path        = local.collection_path
  })
}

resource "local_file" "test_script" {
  count = var.todos_as_local_files ? 1 : 0

  filename        = "test-${var.instance_id}.sh"
  file_permission = "755"
  content         = local.test_script
}

output "endpoint_url" {
  value = "${local.proxy_endpoint_url}/"
}

output "function_arn" {
  value = module.gate_instance.function_arn
}

# assuredly unique within AWS account
output "function_name" {
  value = module.gate_instance.function_name
}

output "instance_role_arn" {
  value = module.gate_instance.iam_role_for_lambda_arn
}

output "instance_role_name" {
  value = module.gate_instance.iam_role_for_lambda_name
}

# in practice, same as function_name; but for simplicity may want something specific to the deployment
output "instance_id" {
  value = module.gate_instance.function_name
}

output "proxy_kind" {
  value       = "webhook-collector"
  description = "The kind of proxy instance this is."
}

output "test_script" {
  value = try(local_file.test_script[0].filename, null)
}

output "test_script_content" {
  value = local.test_script
}

output "output_sanitized_bucket_id" {
  value       = module.sanitized_output.bucket_id
  description = "Bucket ID of the  output bucket for sanitized data."
}

output "provisioned_auth_key_pairs" {
  value       = local.auth_key_arns_sorted
  description = "List of ARNs of kms keys provisioned for webhook authentication purposes, if any."
}

output "todo" {
  value = local.todo_content
}

