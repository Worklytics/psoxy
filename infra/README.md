# infra/

Contains terraform module to setup psoxy instances in various cloud providers. As terraform
modules are NOT cloud agnostic, we follow the same pattern and provide distinct module per
provider, rather than a generic solution or something that takes the provider as a variable.

Please review the `README.md` within each provider's module for pre-reqs and usage details.

## Directory Structure

  - `examples/` - example configurations to be adapted by customers
  - `examples-dev/` - example configurations to be adapted by developers
  - `modules/` - modules to be re-used primarily by other modules or if customers need very custom
     configurations. We do NOT commit to maintaining backwards compatibility of these modules
     interfaces between minor or patch versions. (eg, `0.4.z` variants may break things)
  - `modules-examples/` - psoxy configurations that have been 'modularized', to enable use by
    customers via a minimal root configuration (see `examples/`) OR as part of their larger
    Terraform configuration (presumably covering infra beyond psoxy). If you need more control than
    what variables of these  modules expose, you can copy one of these modules, use it as a root
    terraform configuration, and directly modify how it invokes modules.  We DO commit to maintaining
    the interfaces of these modules between minor patch versions (eg, `0.4.z` variants should not
    break).




