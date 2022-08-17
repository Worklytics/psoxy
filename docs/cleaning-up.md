# Cleaning Up

Done with your Psoxy deployment?

Terraform makes it easy to clean up when you're through with Psoxy, of you wish to rebuild
everything from scratch.

First, a few caveats:
  - this will NOT undo any changes outside of Terraform, even those we instructed you to perform
    via `TODO - ` files that Terraform may have generated.
  - be careful with anything you created outside of Terraform and later imported into Terraform,
    such as GCP project / AWS account themselves. If you DON'T want to destroy these, do
    `terraform state rm <resource>` (analogue of the import) for each.


Do the following to destroy your Psoxy infra:
  1. open you `main.tf` of your terraform confriguation; remove ALL blocks that aren't `terraform`,
     or `provider`. You'll be left with ~30 lines that looks like the following.

```terraform
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.12"
    }

    azuread = {
      version = "~> 2.0"
    }
  }

  backend "local" {
  }
}

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
```

NOTE: do not edit your `terraform.tfvars` file or remove any references to your AWS / Azure / GCP
accounts; Terraform needs be authenticated and know where to delete stuff from!

  2. run `terraform apply`. It'll prompt you with a plan that says "0 to create, 0 to modify" and
     then some huge number of things to destroy. Type 'yes' to apply it.

That's it. It should remove all the Terraform infra you created.

  3. if you want to rebuild from scratch, revert your changes to `main.tf` (`git checkout main.tf`)
     and then `terraform apply` again.
