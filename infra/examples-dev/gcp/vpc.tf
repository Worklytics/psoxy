# VPC egress (optional) — Direct VPC egress for Cloud Functions; outbound via NAT.
#
# Status: composition pattern only (not provisioned inside gcp-host). See docs/gcp/vpc.md.
#
# Uncomment and adapt when data sources must allowlist a fixed egress IP. Then set:
#   vpc_config = {
#     network = google_compute_network.vpc.name
#     subnet  = google_compute_subnetwork.default.name
#   }

# resource "google_compute_network" "vpc" {
#   project                 = var.gcp_project_id
#   name                    = "${local.environment_id_prefix}vpc"
#   auto_create_subnetworks = false
#   mtu                     = 1460
# }
#
# resource "google_compute_subnetwork" "default" {
#   project                  = var.gcp_project_id
#   name                     = "${local.environment_id_prefix}subnet"
#   ip_cidr_range            = "10.10.0.0/24"
#   region                   = var.gcp_region
#   network                  = google_compute_network.vpc.id
#   private_ip_google_access = true
# }
#
# resource "google_compute_router" "router" {
#   project = var.gcp_project_id
#   name    = "${local.environment_id_prefix}router"
#   region  = var.gcp_region
#   network = google_compute_network.vpc.id
# }
#
# resource "google_compute_router_nat" "nat" {
#   project = var.gcp_project_id
#   name    = "${local.environment_id_prefix}nat"
#   router  = google_compute_router.router.name
#   region  = var.gcp_region
#
#   nat_ip_allocate_option             = "AUTO_ONLY"
#   source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
#
#   log_config {
#     enable = true
#     filter = "ERRORS_ONLY"
#   }
# }
