# Historical Features

These are features that have been removed from the codebase, but may be brought back in the future. Kept here for historical reference.


 ## Using HashiCorp Vault with Psoxy

[HashiCorp Vault](https://www.vaultproject.io/) is a popular secret management solution. We've
attempted to implement using it as an alternative to secret management solutions provided by
AWS/GCP.

removed in 0.5.2, because Hashicorp's price increases made maintain test/dev infra to properly cover this unreasonably expensive.
Generally was ultimately not used (by anyone?), and allows us to shrink JAR size, simplify config.

see [hashicorp-vault.md](../removed-features/hashicorp-vault.md)
