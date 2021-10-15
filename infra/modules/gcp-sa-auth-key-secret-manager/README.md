# gcp-sa-auth-key-secret-manager

This terraform module provisions a service account key for a target service account and saves it as
a secret in Secret Manager.

It includes rotation of the key, in days. Every time this module is invoked, it will generate a new
key if your terraform state indicates that your current key is more than the desired number of days
old. The new key will be stored as a new version of the secret in terraform. Hence, if you put `terraform apply` on cron, you'll achieve the desired key rotation.

NOTE: the key values will be written to your terraform state, so ensure you're managing your state
securely in production scenarios.



