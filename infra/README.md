# infra/

Contains terraform module to setup psoxy instances in various cloud providers. As terraform 
modules are NOT cloud agnostic, we follow the same pattern and provide distinct module per
provider, rather than a generic solution or something that takes the provider as a variable.

Please review the `README.md` within each provider's module for pre-reqs and usage details.

