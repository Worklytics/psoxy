
# give pseudonym salt a clearer terraform resource id
moved {
  from = random_password.random
  to   = random_password.pseudonym_salt
}

# google_project_service resource ids style consistent

moved {
  from = google_project_service.gcp-infra-api["cloudresourcemanager.googleapis.com"]
  to   = google_project_service.gcp_infra_api["cloudresourcemanager.googleapis.com"]
}

moved {
  from = google_project_service.gcp-infra-api["iam.googleapis.com"]
  to   = google_project_service.gcp_infra_api["iam.googleapis.com"]
}

moved {
  from = google_project_service.gcp-infra-api["cloudfunctions.googleapis.com"]
  to   = google_project_service.gcp_infra_api["cloudfunctions.googleapis.com"]
}

moved {
  from = google_project_service.gcp-infra-api["secretmanager.googleapis.com"]
  to   = google_project_service.gcp_infra_api["secretmanager.googleapis.com"]
}

moved {
  from = google_project_service.gcp-infra-api["cloudbuild.googleapis.com"]
  to   = google_project_service.gcp_infra_api["cloudbuild.googleapis.com"]
}


# google_secret_manager_secret resource ids style consistent
moved {
  from = google_secret_manager_secret.pseudonymization-salt
  to   = google_secret_manager_secret.pseudonym_salt
}

moved {
  from = google_secret_manager_secret.pseudonymization-key
  to   = google_secret_manager_secret.pseudonymization_key
}

moved {
  from = random_password.pseudonymization-key
  to   = random_password.pseudonym_encryption_key
}

moved {
  from = google_secret_manager_secret_version.pseudonym_encryption_key_initial_version
  to   = google_secret_manager_secret_version.pseudonym_encryption_key_initial_version
}
