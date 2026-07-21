# External API ALB ingress (optional, beta) — global external ALB + Cloud Armor (WAF).
#
# Status: composition pattern only; not a first-class module. See docs/development/gcp-external-alb.md.
#
# Orthogonal to vpc.tf (egress). Enable via enable_external_api_alb in terraform.tfvars.
# After apply, endpoint URLs use the reserved global IP (self-signed PoC) or api_proxy_domain
# (managed TLS). To auto-wire that host into gcp-host, set module.psoxy's
# api_connector_external_lb_host to local.api_connector_external_lb_host in main.tf (see below).
#
# Worklytics reaches API connectors over the public internet from static egress IPs.
# Cloud Armor enforces allowed_data_access_ip_blocks at the network layer; pass the
# same list into module.psoxy for application-layer enforcement.
#
# URL map routes /<function-name>/* to each connector; the proxy strips that prefix
# using K_SERVICE.
#
# TLS:
#   - Production: set api_proxy_domain + DNS A record → Google-managed cert
#   - PoC: omit domain → self-signed cert with SAN = reserved global IP
#     (test with psoxy-test --allow-insecure-tls or --cacert)
#
# Future work: provision this ALB inside gcp-host instead of root composition
# (see docs/development/gcp-external-alb.md).



variable "enable_external_api_alb" {
  type        = bool
  description = "Provision the external ALB + Cloud Armor resources in this file."
  default     = false
}

variable "api_proxy_domain" {
  type        = string
  description = "Optional. Google-managed HTTPS when set; self-signed HTTPS on global IP when null (PoC only)."
  default     = null
  nullable    = true
}

locals {
  # Matches gcp-host environment_id_prefix; use for root-composed resource names.
  environment_id_prefix = "${var.environment_name}${length(var.environment_name) > 0 ? "-" : ""}"

  api_connector_function_names = {
    for k in keys(local.api_connectors) :
    k => "${local.environment_id_prefix}${k}"
  }

  external_api_alb_enabled = (
    var.enable_external_api_alb
    && length(local.api_connector_function_names) > 0
    && var.allowed_data_access_ip_blocks != null
  )

  external_api_alb_managed_https = local.external_api_alb_enabled && var.api_proxy_domain != null
  external_api_alb_self_signed   = local.external_api_alb_enabled && var.api_proxy_domain == null

  # Hostname/IP for gcp-host when this composition is enabled (domain or reserved global IP).
  # main.tf passes var.api_connector_external_lb_host by default so this file can stay optional.
  # When using this ALB, replace that with local.api_connector_external_lb_host in module.psoxy.
  api_connector_external_lb_host = local.external_api_alb_enabled ? (
    var.api_proxy_domain != null ? var.api_proxy_domain : google_compute_global_address.api_proxy[0].address
  ) : null
}

check "external_api_alb_config" {
  assert {
    condition     = !var.enable_external_api_alb || var.allowed_data_access_ip_blocks != null
    error_message = "When enable_external_api_alb is true, set allowed_data_access_ip_blocks (Worklytics static egress IPs, or your own IP for demo)."
  }
}

resource "google_project_service" "certificatemanager" {
  count = local.external_api_alb_managed_https ? 1 : 0

  project                    = var.gcp_project_id
  service                    = "certificatemanager.googleapis.com"
  disable_dependent_services = false
  disable_on_destroy         = false
}

resource "google_compute_global_address" "api_proxy" {
  count = local.external_api_alb_enabled ? 1 : 0

  project = var.gcp_project_id
  name    = "${local.environment_id_prefix}api-alb"
}

resource "google_compute_security_policy" "worklytics_ingress" {
  count = local.external_api_alb_enabled ? 1 : 0

  project = var.gcp_project_id
  name    = "${local.environment_id_prefix}worklytics-ingress"
}

resource "google_compute_security_policy_rule" "worklytics_ingress_allow" {
  count = local.external_api_alb_enabled ? 1 : 0

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
  count = local.external_api_alb_enabled ? 1 : 0

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
  for_each = local.external_api_alb_enabled ? local.api_connector_function_names : {}

  project               = var.gcp_project_id
  name                  = "${each.value}-neg"
  region                = var.gcp_region
  network_endpoint_type = "SERVERLESS"

  cloud_run {
    service = each.value
  }

  depends_on = [module.psoxy]
}

resource "google_compute_backend_service" "api_connector" {
  for_each = local.external_api_alb_enabled ? local.api_connector_function_names : {}

  project               = var.gcp_project_id
  name                  = "${each.value}-backend"
  protocol              = "HTTP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  security_policy       = google_compute_security_policy.worklytics_ingress[0].id

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
  count = local.external_api_alb_enabled ? 1 : 0

  project = var.gcp_project_id
  name    = "${local.environment_id_prefix}api-connectors"

  default_service = values(google_compute_backend_service.api_connector)[0].id

  host_rule {
    hosts        = local.external_api_alb_managed_https ? [var.api_proxy_domain] : ["*"]
    path_matcher = "api-connectors"
  }

  path_matcher {
    name            = "api-connectors"
    default_service = values(google_compute_backend_service.api_connector)[0].id

    dynamic "path_rule" {
      for_each = local.api_connector_function_names
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

# Google-managed TLS (production): set api_proxy_domain + DNS A record

resource "google_certificate_manager_certificate" "api_proxy" {
  count = local.external_api_alb_managed_https ? 1 : 0

  project = var.gcp_project_id
  name    = "${local.environment_id_prefix}api-proxy-cert"

  managed {
    domains = [var.api_proxy_domain]
  }

  depends_on = [google_project_service.certificatemanager]
}

resource "google_certificate_manager_certificate_map" "api_proxy" {
  count = local.external_api_alb_managed_https ? 1 : 0

  project = var.gcp_project_id
  name    = "${local.environment_id_prefix}api-proxy-cert-map"
}

resource "google_certificate_manager_certificate_map_entry" "api_proxy" {
  count = local.external_api_alb_managed_https ? 1 : 0

  project      = var.gcp_project_id
  name         = "${local.environment_id_prefix}api-proxy-cert-entry"
  map          = google_certificate_manager_certificate_map.api_proxy[0].name
  certificates = [google_certificate_manager_certificate.api_proxy[0].id]
  hostname     = var.api_proxy_domain
}

resource "google_compute_target_https_proxy" "api_connectors_managed" {
  count = local.external_api_alb_managed_https ? 1 : 0

  project         = var.gcp_project_id
  name            = "${local.environment_id_prefix}api-connectors-https"
  url_map         = google_compute_url_map.api_connectors[0].id
  certificate_map = "//certificatemanager.googleapis.com/${google_certificate_manager_certificate_map.api_proxy[0].id}"
}

# Self-signed TLS (PoC): omit api_proxy_domain; cert SAN includes the reserved global IP

resource "tls_private_key" "api_proxy" {
  count = local.external_api_alb_self_signed ? 1 : 0

  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "api_proxy" {
  count = local.external_api_alb_self_signed ? 1 : 0

  private_key_pem = tls_private_key.api_proxy[0].private_key_pem

  subject {
    common_name = "${local.environment_id_prefix}api-proxy.poc"
  }

  validity_period_hours = 8760

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]

  ip_addresses = [google_compute_global_address.api_proxy[0].address]
}

resource "google_compute_ssl_certificate" "api_proxy_self_signed" {
  count = local.external_api_alb_self_signed ? 1 : 0

  project     = var.gcp_project_id
  name        = "${local.environment_id_prefix}api-proxy-self-signed"
  private_key = tls_private_key.api_proxy[0].private_key_pem
  certificate = tls_self_signed_cert.api_proxy[0].cert_pem

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_target_https_proxy" "api_connectors_self_signed" {
  count = local.external_api_alb_self_signed ? 1 : 0

  project = var.gcp_project_id
  name    = "${local.environment_id_prefix}api-connectors-https"
  url_map = google_compute_url_map.api_connectors[0].id

  ssl_certificates = [google_compute_ssl_certificate.api_proxy_self_signed[0].id]
}

resource "google_compute_global_forwarding_rule" "api_connectors_https" {
  count = local.external_api_alb_enabled ? 1 : 0

  project               = var.gcp_project_id
  name                  = "${local.environment_id_prefix}api-connectors-https"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  ip_protocol           = "TCP"
  port_range            = "443"
  ip_address            = google_compute_global_address.api_proxy[0].id
  target = coalesce(
    try(google_compute_target_https_proxy.api_connectors_managed[0].id, null),
    try(google_compute_target_https_proxy.api_connectors_self_signed[0].id, null),
  )
}

output "external_alb_ip_address" {
  description = "Reserved global IP for the external load balancer."
  value       = try(google_compute_global_address.api_proxy[0].address, null)
}

output "external_alb_dns_setup" {
  description = "DNS record required before the Google-managed certificate can provision (managed TLS mode only)."
  value = local.external_api_alb_managed_https ? trimspace(<<-EOT
    Create a DNS record for ${var.api_proxy_domain}:
      Type:  A
      Name:  ${var.api_proxy_domain}
      Value: ${google_compute_global_address.api_proxy[0].address}

    Certificate provisioning may take 15–60 minutes after DNS propagates.
  EOT
  ) : null
}

output "external_alb_self_signed_ca_cert" {
  description = "Self-signed server certificate (PEM). Trust with psoxy-test --cacert, or use --allow-insecure-tls for quick PoC checks."
  value       = try(tls_self_signed_cert.api_proxy[0].cert_pem, null)
  sensitive   = true
}
