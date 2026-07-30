/**
 * Global external Application Load Balancer in front of GCP API connectors, with optional Cloud Armor.
 *
 * Caller reserves the global IP (to resolve connector endpoint host without a module cycle) and
 * ensures Cloud Run / Cloud Functions services exist before applying this module (depends_on).
 *
 * @see docs/development/gcp-external-alb.md
 */

terraform {
  required_version = "~> 1.7"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 7.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

locals {
  cloud_armor_enabled = var.allowed_data_access_ip_blocks != null
  managed_https       = var.domain != null
  self_signed         = var.domain == null
}

resource "google_project_service" "certificatemanager" {
  count = local.managed_https ? 1 : 0

  project                    = var.gcp_project_id
  service                    = "certificatemanager.googleapis.com"
  disable_dependent_services = false
  disable_on_destroy         = false
}

# Cloud Armor only when an IP allowlist is configured. With null IPs we omit the
# policy entirely (backends have no security_policy) so all internet source IPs
# pass the LB.
resource "google_compute_security_policy" "worklytics_ingress" {
  count = local.cloud_armor_enabled ? 1 : 0

  project = var.gcp_project_id
  name    = "${var.environment_id_prefix}worklytics-ingress"
}

resource "google_compute_security_policy_rule" "worklytics_ingress_allow" {
  count = local.cloud_armor_enabled ? 1 : 0

  security_policy = google_compute_security_policy.worklytics_ingress[0].name
  project         = var.gcp_project_id
  priority        = 1000
  action          = "allow"
  description     = "Allow Worklytics static egress IPs"

  match {
    versioned_expr = "SRC_IPS_V1"
    config {
      src_ip_ranges = var.allowed_data_access_ip_blocks
    }
  }
}

resource "google_compute_security_policy_rule" "worklytics_ingress_deny" {
  count = local.cloud_armor_enabled ? 1 : 0

  security_policy = google_compute_security_policy.worklytics_ingress[0].name
  project         = var.gcp_project_id
  priority        = 2147483647
  action          = "deny(403)"
  description     = "Default deny"

  match {
    versioned_expr = "SRC_IPS_V1"
    config {
      src_ip_ranges = ["*"]
    }
  }
}

resource "google_compute_region_network_endpoint_group" "api_connector" {
  for_each = var.api_connector_function_names

  project               = var.gcp_project_id
  name                  = "${each.value}-neg"
  region                = var.gcp_region
  network_endpoint_type = "SERVERLESS"

  cloud_run {
    service = each.value
  }
}

resource "google_compute_backend_service" "api_connector" {
  for_each = var.api_connector_function_names

  project               = var.gcp_project_id
  name                  = "${each.value}-backend"
  protocol              = "HTTP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  security_policy       = try(google_compute_security_policy.worklytics_ingress[0].id, null)

  backend {
    group           = google_compute_region_network_endpoint_group.api_connector[each.key].id
    balancing_mode  = "UTILIZATION"
    capacity_scaler = 1.0
  }

  log_config {
    enable      = true
    sample_rate = 1.0
  }
}

resource "google_compute_url_map" "api_connectors" {
  project = var.gcp_project_id
  name    = "${var.environment_id_prefix}api-connectors"

  default_service = values(google_compute_backend_service.api_connector)[0].id

  host_rule {
    hosts        = local.managed_https ? [var.domain] : ["*"]
    path_matcher = "api-connectors"
  }

  path_matcher {
    name            = "api-connectors"
    default_service = values(google_compute_backend_service.api_connector)[0].id

    dynamic "path_rule" {
      for_each = var.api_connector_function_names
      content {
        paths = [
          "/${path_rule.value}",
          "/${path_rule.value}/*",
        ]
        service = google_compute_backend_service.api_connector[path_rule.key].id
      }
    }
  }
}

# Google-managed TLS (production): set domain + DNS A record

resource "google_certificate_manager_certificate" "api_proxy" {
  count = local.managed_https ? 1 : 0

  project = var.gcp_project_id
  name    = "${var.environment_id_prefix}api-proxy-cert"

  managed {
    domains = [var.domain]
  }

  depends_on = [google_project_service.certificatemanager]
}

resource "google_certificate_manager_certificate_map" "api_proxy" {
  count = local.managed_https ? 1 : 0

  project = var.gcp_project_id
  name    = "${var.environment_id_prefix}api-proxy-cert-map"
}

resource "google_certificate_manager_certificate_map_entry" "api_proxy" {
  count = local.managed_https ? 1 : 0

  project      = var.gcp_project_id
  name         = "${var.environment_id_prefix}api-proxy-cert-entry"
  map          = google_certificate_manager_certificate_map.api_proxy[0].name
  certificates = [google_certificate_manager_certificate.api_proxy[0].id]
  hostname     = var.domain
}

resource "google_compute_target_https_proxy" "api_connectors_managed" {
  count = local.managed_https ? 1 : 0

  project         = var.gcp_project_id
  name            = "${var.environment_id_prefix}api-connectors-https"
  url_map         = google_compute_url_map.api_connectors.id
  certificate_map = "//certificatemanager.googleapis.com/${google_certificate_manager_certificate_map.api_proxy[0].id}"
}

# Self-signed TLS (PoC): omit domain; cert SAN includes the reserved global IP

resource "tls_private_key" "api_proxy" {
  count = local.self_signed ? 1 : 0

  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "api_proxy" {
  count = local.self_signed ? 1 : 0

  private_key_pem = tls_private_key.api_proxy[0].private_key_pem

  subject {
    common_name = "${var.environment_id_prefix}api-proxy.poc"
  }

  validity_period_hours = 8760

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]

  ip_addresses = [var.global_address_ip]
}

resource "google_compute_ssl_certificate" "api_proxy_self_signed" {
  count = local.self_signed ? 1 : 0

  project     = var.gcp_project_id
  name        = "${var.environment_id_prefix}api-proxy-self-signed"
  private_key = tls_private_key.api_proxy[0].private_key_pem
  certificate = tls_self_signed_cert.api_proxy[0].cert_pem

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_target_https_proxy" "api_connectors_self_signed" {
  count = local.self_signed ? 1 : 0

  project = var.gcp_project_id
  name    = "${var.environment_id_prefix}api-connectors-https"
  url_map = google_compute_url_map.api_connectors.id

  ssl_certificates = [google_compute_ssl_certificate.api_proxy_self_signed[0].id]
}

resource "google_compute_global_forwarding_rule" "api_connectors_https" {
  project               = var.gcp_project_id
  name                  = "${var.environment_id_prefix}api-connectors-https"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  ip_protocol           = "TCP"
  port_range            = "443"
  ip_address            = var.global_address_id
  target = coalesce(
    try(google_compute_target_https_proxy.api_connectors_managed[0].id, null),
    try(google_compute_target_https_proxy.api_connectors_self_signed[0].id, null),
  )
}
