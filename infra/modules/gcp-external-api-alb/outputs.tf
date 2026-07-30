output "ip_address" {
  description = "Reserved global IP for the external load balancer (same as caller's global_address_ip)."
  value       = var.global_address_ip
}

output "host" {
  description = "Hostname or IP clients should use (domain when set, else reserved global IP)."
  value       = coalesce(var.domain, var.global_address_ip)
}

output "todo_dns_setup" {
  description = "DNS record required before the Google-managed certificate can provision (managed TLS mode only)."
  value = local.managed_https ? trimspace(<<-EOT
    Create a DNS record for ${var.domain}:
      Type:  A
      Name:  ${var.domain}
      Value: ${var.global_address_ip}

    Certificate provisioning may take 15–60 minutes after DNS propagates.
  EOT
  ) : null
}

output "self_signed_ca_cert" {
  description = "Self-signed server certificate (PEM). Trust with psoxy-test --cacert, or use --allow-insecure-tls for quick PoC checks."
  value       = try(tls_self_signed_cert.api_proxy[0].cert_pem, null)
  sensitive   = true
}
