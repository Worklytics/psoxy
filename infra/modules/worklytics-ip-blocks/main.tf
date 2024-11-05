# Add the http provider
terraform {
  required_providers {
    http = {
      source  = "hashicorp/http"
      version = "~> 3.0"
    }
  }
}

# Fetch the JSON data from the URL
data "http" "google_cloud_ip_ranges" {
  url = "https://www.gstatic.com/ipranges/cloud.json"
}

locals {
  ips_by_tenant_location = {
    us = [
      for prefix in jsondecode(data.http.google_cloud_ip_ranges.response_body).prefixes : prefix if startswith(prefix.scope, "us")
    ]
    eu = [
      for prefix in jsondecode(data.http.google_cloud_ip_ranges.response_body).prefixes : prefix if startswith(prefix.scope, "eu")
    ]
  }

}

# Output example
output "cidr_blocks_by_tenant_location" {
  value = [for i in local.ips_by_tenant_location[var.tenant_location] : try(i.ipv4Prefix, i.ipv6Prefix)]
}
