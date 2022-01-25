terraform {
  required_providers {
    # for the infra that will host Psoxy instances
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.0"
    }

    # for API connections to Microsoft 365
    azuread = {
      version = "~> 2.0"
    }
  }

  # if you leave this as local, you should backup/commit your TF state files
  backend "local" {
  }
}

# NOTE: you need to provide credentials. usual way to do this is to set env vars:
#        AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# see https://registry.terraform.io/providers/hashicorp/aws/latest/docs#authentication for more
# information as well as alternative auth approaches
provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = var.aws_assume_role_arn
  }
  allowed_account_ids = [
    var.aws_account_id
  ]
}

provider "azuread" {
  tenant_id = var.msft_tenant_id
}

module "psoxy-aws" {
  source = "../../modules/aws"

  caller_aws_account_id   = var.caller_aws_account_id
  caller_external_user_id = var.caller_external_user_id
  aws_account_id          = var.aws_account_id

  providers = {
    aws = aws
  }
}

module "psoxy-package" {
  source = "../../modules/psoxy-package"

  implementation     = "aws"
  path_to_psoxy_java = "../../../java"
}

locals {
  # Microsoft 365 sources; add/remove as you wish
  msft_sources = {
    "azure-ad" : {
      display_name: "Azure Directory"
      required_resource_access: [
        {
          id   : "User.Read.All",
          type : "Role"
        },
        {
          id   : "Group.Read.All",
          type : "Role"
        }
      ]
    },
    "outlook-cal" : {
      display_name: "Outlook Calendar"
      required_resource_access: [
        {
          id   : "User.Read.All",
          type : "Role"
        },
        {
          id   : "Calendars.Read",
          type : "Role"
        },
        {
          id   : "MailboxSettings.Read",
          type : "Role"
        },
        {
          id   : "OnlineMeetings.Read.All",
          type : "Role"
        },
        {
          id   : "Group.Read.All",
          type : "Role"
        }
      ]
    },
    "outlook-mail-meta" : {
      display_name: "Outlook Mail"
      required_resource_access: [
        {
          id   : "User.Read.All",
          type : "Role"
        },
        {
          id   : "Mail.ReadBasic.All",
          type : "Role"
        },
        {
          id   : "MailboxSettings.Read",
          type : "Role"
        },
        {
          id   : "Group.Read.All",
          type : "Role"
        }
      ]
    }
  }
}

module "msft-connection" {
  for_each = local.msft_sources

  source = "../../modules/azuread-connection"

  display_name             = "Psoxy Connector - ${each.value.display_name}${var.connector_display_name_suffix}"
  required_resources       = each.value.required_resource_access
  tenant_id                 = var.msft_tenant_id
}

module "msft-connection-auth" {
  for_each = local.msft_sources

  source = "../../modules/azuread-local-cert"

  application_id       = module.msft-connection[each.key].connector.id
  rotation_days        = 60
  cert_expiration_days = 180
  certificate_subject  = var.certificate_subject
}

module "private-key-aws-parameters" {
  for_each = local.msft_sources

  source = "../../modules/private-key-aws-parameter"

  instance_id = each.key
  
  private_key_id = module.msft-connection-auth[each.key].private_key_id
  private_key    = module.msft-connection-auth[each.key].private_key
}

module "psoxy-msft-connector" {
  for_each = local.msft_sources

  source = "../../modules/aws-psoxy-instance"

  function_name        = "psoxy-${each.key}"
  source_kind          = each.key
  api_gateway          = module.psoxy-aws.api_gateway
  path_to_function_zip = module.psoxy-package.path_to_deployment_jar
  function_zip_hash    = module.psoxy-package.deployment_package_hash
  path_to_config       = "../../../configs/${each.key}.yaml"
  api_caller_role_arn  = module.psoxy-aws.api_caller_role_arn
  aws_assume_role_arn  = var.aws_assume_role_arn

  parameters = concat(
    module.private-key-aws-parameters[each.key].parameters,
    [
      module.psoxy-aws.salt_secret,
    ]
  )
}
