/** Vault dev config for local testing **/

#terraform {
#  required_providers {
#    vault = {
#      source  = "hashicorp/vault"
#      version = "~> 3.11.0"
#    }
#  }
#}


# used for peering to Vault Cloud HVN
resource "aws_vpc" "psoxy_vpc" {
  cidr_block = var.vpc_ip_block
}

provider "vault" {
  address = var.vault_addr

  # NOTE: set a token in your env vars, eg
  # export VAULT_TOKEN=...
}

#module "aws_vault_auth" {
#  source = "../../modules/aws-vault-auth"
#
#  aws_vault_role_arn = var.aws_vault_role_arn
#}
#
#module "vault_psoxy" {
#  for_each = module.psoxy-aws-google-workspace.instances
#
#  source = "../../modules/vault-psoxy"
#
#  instance_id           = each.key
#}
#
## BEGIN AWS iam-based Auth
#module "aws_vault_connection_gcal" {
#  for_each = module.psoxy-aws-google-workspace.instances
#
#  source = "../../modules/aws-vault-access"
#
#  aws_auth_backend_name = module.aws_vault_auth.vault_aws_auth_backend_path
#  instance_id           = each.key
#  role_arn              = each.value.instance_role_arn
#  vault_policy_name     = module.vault_psoxy[each.key].vault_policy_name
#}
## END AWS iam-based Auth

#
## BEGIN periodic token-based vault auth
#module "aws_vault_token" {
#  for_each = module.psoxy-aws-google-workspace.instances
#
#  source = "../../modules/vault-access-token"
#
#  instance_id           = each.key
#
#  vault_policy_name     = module.vault_psoxy[each.key].vault_policy_name
#}
#
#locals {
#  tokens_to_set = [ for k, v in module.aws_vault_token : " - on `${k}` set `VAULT_TOKEN` = `${v.vault_token}`"]
#}
#
## unfortunately, no way to backfill these on per-lambda basis without deeper refactor
#resource "local_file" "fill_token_vars" {
#  filename = "TODO 0 - fill VAULT_TOKEN env vars.md"
#  content = <<EOT
## TODO - fill token env vars on lambdas
#
#Via AWS console --> Lambdas:
#${join("\n", local.tokens_to_set)}
#
#EOT
#}
#
## END periodic token-based vault auth
