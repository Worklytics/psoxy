See https://github.com/Worklytics/psoxy-example-aws

This is a dev version of that template repo.


## Structure

This example Terraform configuration has a non-standard structure. Rather than grouping all Terraform
resources into a `main.tf`, variables into `variables.tf`, etc, these are split by components that
can potentially be decoupled.

  - `main.tf` / `variables.tf` / `outputs.tf` - the 'core' stuff, which we expect every
     configuration to require; and piping to
  - `{component-identifier}.tf`, `{component-identifier}-variables.tf`, `{component-identifier}-outputs.tf` -
     the resources/variables/outputs for a given component. If you don't need the component, then
     `rm {component-identifier}*.tf`

As of June 2023, this applies to two sources:
  - `msft-365`
  - `google-workspace`
