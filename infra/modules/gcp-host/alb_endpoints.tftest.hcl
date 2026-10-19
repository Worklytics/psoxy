# ALB endpoint URL and ingress behavior in gcp-host:
# - managed TLS domain (external_api_alb.domain)
# - self-signed PoC on reserved global IP (external_api_alb = {})
# - BYO host (api_connector_external_lb_host)
#
# The external_api_alb = {} case is the reserved-IP branch: the global address is unknown
# until apply, so ingress must be decided from plan-time inputs (not from the IP value).

variables {
  gcp_project_id       = "test-project-123456"
  environment_name     = "test"
  worklytics_sa_emails = ["test@example.com"]
  psoxy_base_dir       = "../../../"
  todos_as_local_files = false

  api_connectors = {
    "test-gmail" = {
      source_kind          = "gmail"
      source_auth_strategy = "gcp-sa"
      target_host          = "gmail.googleapis.com"
      example_api_calls    = ["/gmail/v1/users/me/messages"]
    }
  }

  bulk_connectors    = {}
  webhook_collectors = {}
}

mock_provider "google" {
  mock_data "google_project" {
    defaults = {
      project_id = "test-project-123456"
      number     = 123456789
    }
  }

  mock_data "google_compute_default_service_account" {
    defaults = {
      email = "123456789-compute@developer.gserviceaccount.com"
      name  = "projects/test-project-123456/serviceAccounts/123456789-compute@developer.gserviceaccount.com"
    }
  }
}

mock_provider "tls" {}

run "alb_domain_used_in_host_todos_and_outputs" {
  command = plan

  variables {
    external_api_alb = {
      domain = "proxy.example.com"
    }
  }

  assert {
    error_message = "api_connector_instances.endpoint_url should use the ALB domain"
    condition     = output.api_connector_instances["test-gmail"].endpoint_url == "https://proxy.example.com/test-test-gmail/"
  }

  assert {
    error_message = "connector test TODOs should reference the ALB domain"
    condition     = strcontains(module.api_connector["test-gmail"].todo, "https://proxy.example.com/test-test-gmail")
  }

  assert {
    error_message = "connector test TODOs must not tell operators to call *.run.app when the ALB is enabled"
    condition     = !strcontains(module.api_connector["test-gmail"].todo, ".run.app")
  }
}

run "external_api_alb_empty_reserved_ip_plan" {
  command = plan

  variables {
    external_api_alb = {}
  }

  assert {
    error_message = "external_api_alb = {} should reserve a global address for self-signed PoC"
    condition     = length(google_compute_global_address.api_connector_alb) == 1
  }

  assert {
    error_message = "external_api_alb = {} should provision the external_api_alb module"
    condition     = length(module.external_api_alb) == 1
  }

  assert {
    error_message = "external_api_alb = {} must set ALLOW_INTERNAL_AND_GCLB at plan time without waiting for the reserved IP"
    condition     = module.api_connector["test-gmail"].function_config.service_config[0].ingress_settings == "ALLOW_INTERNAL_AND_GCLB"
  }
}

run "byo_alb_host_used_in_endpoint_url" {
  command = plan

  variables {
    api_connector_external_lb_host = "alb.customer.example"
  }

  assert {
    error_message = "BYO ALB host should appear in endpoint_url"
    condition     = output.api_connector_instances["test-gmail"].endpoint_url == "https://alb.customer.example/test-test-gmail/"
  }

  assert {
    error_message = "BYO ALB host should appear in test TODOs"
    condition     = strcontains(module.api_connector["test-gmail"].todo, "https://alb.customer.example/test-test-gmail")
  }
}
