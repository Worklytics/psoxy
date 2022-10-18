# stores secrets as GCP Secrets Manager secretsers
# NOTE: value of this module is a consistent interface across potential Secret store implementations
#   eg, GCP Secret Manager, AWS SSM Parameter Store, Hashicorp Vault, etc.
#  but is this good Terraform style? clearly in AWS case, this module doesn't do much ...

resource "google_secret_manager_secret" "secret" {
  for_each = var.secrets

  project   = var.secret_project
  secret_id = each.key

  replication {
    user_managed {
      dynamic "replicas" {
        for_each = var.replica_regions
        content {
          location = replicas.value
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      replication, # for backwards compatibility; replication can't be changed after secrets created
      labels
    ]
  }
}

resource "google_secret_manager_secret_version" "version" {
  for_each = var.secrets

  secret      = google_secret_manager_secret.secret[each.key].id
  secret_data = each.value

  lifecycle {
    create_before_destroy = true
  }
}

# for use in explicit IAM policy grants
output "secret_ids" {
  value = { for k, v in var.secrets : k => google_secret_manager_secret.secret[k].id }
}

