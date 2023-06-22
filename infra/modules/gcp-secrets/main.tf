# stores secrets as GCP Secrets Manager secrets
# NOTE: value of this module is a consistent interface across potential Secret store implementations
#   eg, GCP Secret Manager, AWS SSM Parameter Store, Hashicorp Vault, etc.
#  but is this good Terraform style? clearly in AWS case, this module doesn't do much ...

resource "google_secret_manager_secret" "secret" {
  for_each = var.secrets

  project   = var.secret_project
  secret_id = "${var.path_prefix}${each.key}"
  labels    = var.default_labels

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

# TODO: avoid creating version here at all if value == null
# (problem is that Terraform complains if trying to use any derivative of var.secrets in a for_each,
#  bc it's sensitive - not sure why it doesn't complain about the for_each over var.secrets directly)
resource "google_secret_manager_secret_version" "version" {
  for_each = var.secrets

  secret      = google_secret_manager_secret.secret[each.key].id
  secret_data = coalesce(each.value.value, "placeholder value - fill me")
  # NOTE: can't set `enabled = false` here, bc we bind secret to env var so CloudFunction update will fail

  lifecycle {
    create_before_destroy = true
  }
}

# for use in explicit IAM policy grants
output "secret_ids" { # prefixed w `projects/{numeric_id}/secrets/`
  value = { for k, v in var.secrets : k => google_secret_manager_secret.secret[k].id }
}

#DEPRECATED; use `secret_ids_within_project` instead
output "secret_secret_ids" {
  # relative to project
  value = { for k, v in var.secrets : k => google_secret_manager_secret.secret[k].secret_id }
}

output "secret_ids_within_project" {
  # relative to project
  value = { for k, v in var.secrets : k => google_secret_manager_secret.secret[k].secret_id }
}

output "secret_version_names" {
  value = { for k, v in var.secrets : k => google_secret_manager_secret_version.version[k].name }
}

output "secret_version_numbers" {
  value = { for k, v in var.secrets :
  k => trimprefix(google_secret_manager_secret_version.version[k].name, "${google_secret_manager_secret.secret[k].name}/versions/") }
}

# map from secret's identifier in var.secrets --> object(secret_id, version_number)
output "secret_bindings" {
  value = { for k, v in var.secrets :
    k => {
      secret_id      = google_secret_manager_secret.secret[k].secret_id
      version_number = trimprefix(google_secret_manager_secret_version.version[k].name, "${google_secret_manager_secret.secret[k].name}/versions/")
    }
  }
}
