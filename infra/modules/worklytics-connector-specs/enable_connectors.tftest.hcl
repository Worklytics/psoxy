# Test file for validating enabled_connectors and enabled_connectors_from_templates functionality
# Tests the worklytics-connector-specs module to ensure both approaches work correctly

# Test 1: Standard enabled_connectors approach
run "standard_enabled_connectors" {
  variables {
    enabled_connectors = ["gcal", "outlook-cal", "zoom"]
    enabled_connectors_from_templates = {}
    include_google_workspace = true
    include_msft = true
    msft_tenant_id = "test-tenant-id"
  }

  assert {
    error_message = "Should have 3 enabled Google Workspace connectors (gcal)"
    condition = length(output.enabled_google_workspace_connectors) == 1
  }

  assert {
    error_message = "Should have 1 enabled Microsoft 365 connector (outlook-cal)"
    condition = length(output.enabled_msft_365_connectors) == 1
  }

  assert {
    error_message = "Should have 1 enabled OAuth connector (zoom)"
    condition = length(output.enabled_oauth_long_access_connectors) == 1
  }

  assert {
    error_message = "gcal connector should be enabled with correct ID"
    condition = contains(keys(output.enabled_google_workspace_connectors), "gcal")
  }

  assert {
    error_message = "outlook-cal connector should be enabled with correct ID"
    condition = contains(keys(output.enabled_msft_365_connectors), "outlook-cal")
  }

  assert {
    error_message = "zoom connector should be enabled with correct ID"
    condition = contains(keys(output.enabled_oauth_long_access_connectors), "zoom")
  }

  assert {
    error_message = "gcal connector should have correct worklytics_connector_id"
    condition = output.enabled_google_workspace_connectors["gcal"].worklytics_connector_id == "gcal-psoxy"
  }

  assert {
    error_message = "outlook-cal connector should have correct worklytics_connector_id"
    condition = output.enabled_msft_365_connectors["outlook-cal"].worklytics_connector_id == "outlook-cal-psoxy"
  }

  assert {
    error_message = "zoom connector should have correct worklytics_connector_id"
    condition = output.enabled_oauth_long_access_connectors["zoom"].worklytics_connector_id == "zoom-psoxy"
  }
}

# Test 2: enabled_connectors_from_templates approach with custom IDs
run "template_enabled_connectors" {
  variables {
    enabled_connectors = []
    enabled_connectors_from_templates = {
      "zoom2" = {
        template_id = "zoom"
      }
      "gcal2" = {
        template_id = "gcal"
      }
      "outlook-cal2" = {
        template_id = "outlook-cal"
      }
    }
    include_google_workspace = true
    include_msft = true
    msft_tenant_id = "test-tenant-id"
  }

  assert {
    error_message = "Should have 1 enabled Google Workspace connector (gcal2)"
    condition = length(output.enabled_google_workspace_connectors) == 1
  }

  assert {
    error_message = "Should have 1 enabled Microsoft 365 connector (outlook-cal2)"
    condition = length(output.enabled_msft_365_connectors) == 1
  }

  assert {
    error_message = "Should have 1 enabled OAuth connector (zoom2)"
    condition = length(output.enabled_oauth_long_access_connectors) == 1
  }

  assert {
    error_message = "gcal2 connector should be enabled with custom ID"
    condition = contains(keys(output.enabled_google_workspace_connectors), "gcal2")
  }

  assert {
    error_message = "outlook-cal2 connector should be enabled with custom ID"
    condition = contains(keys(output.enabled_msft_365_connectors), "outlook-cal2")
  }

  assert {
    error_message = "zoom2 connector should be enabled with custom ID"
    condition = contains(keys(output.enabled_oauth_long_access_connectors), "zoom2")
  }

  assert {
    error_message = "gcal2 connector should have correct worklytics_connector_id from template"
    condition = output.enabled_google_workspace_connectors["gcal2"].worklytics_connector_id == "gcal-psoxy"
  }

  assert {
    error_message = "outlook-cal2 connector should have correct worklytics_connector_id from template"
    condition = output.enabled_msft_365_connectors["outlook-cal2"].worklytics_connector_id == "outlook-cal-psoxy"
  }

  assert {
    error_message = "zoom2 connector should have correct worklytics_connector_id from template"
    condition = output.enabled_oauth_long_access_connectors["zoom2"].worklytics_connector_id == "zoom-psoxy"
  }

  assert {
    error_message = "gcal2 connector should have correct source_kind from template"
    condition = output.enabled_google_workspace_connectors["gcal2"].source_kind == "gcal"
  }

  assert {
    error_message = "outlook-cal2 connector should have correct source_kind from template"
    condition = output.enabled_msft_365_connectors["outlook-cal2"].source_kind == "outlook-cal"
  }

  assert {
    error_message = "zoom2 connector should have correct source_kind from template"
    condition = output.enabled_oauth_long_access_connectors["zoom2"].source_kind == "zoom"
  }
}

# Test 3: Combined approach - both enabled_connectors and enabled_connectors_from_templates
run "combined_enabled_connectors" {
  variables {
    enabled_connectors = ["zoom"]
    enabled_connectors_from_templates = {
      "zoom2" = {
        template_id = "zoom"
      }
      "gcal" = {
        template_id = "gcal"
      }
    }
    include_google_workspace = true
    include_msft = true
    msft_tenant_id = "test-tenant-id"
  }

  assert {
    error_message = "Should have 1 enabled Google Workspace connector (gcal from template)"
    condition = length(output.enabled_google_workspace_connectors) == 1
  }

  assert {
    error_message = "Should have 2 enabled OAuth connectors (zoom from standard + zoom2 from template)"
    condition = length(output.enabled_oauth_long_access_connectors) == 2
  }

  assert {
    error_message = "gcal connector should be enabled from template"
    condition = contains(keys(output.enabled_google_workspace_connectors), "gcal")
  }

  assert {
    error_message = "zoom connector should be enabled from standard approach"
    condition = contains(keys(output.enabled_oauth_long_access_connectors), "zoom")
  }

  assert {
    error_message = "zoom2 connector should be enabled from template"
    condition = contains(keys(output.enabled_oauth_long_access_connectors), "zoom2")
  }

  assert {
    error_message = "Both zoom connectors should have same worklytics_connector_id"
    condition = output.enabled_oauth_long_access_connectors["zoom"].worklytics_connector_id == output.enabled_oauth_long_access_connectors["zoom2"].worklytics_connector_id
  }

  assert {
    error_message = "Both zoom connectors should have same source_kind"
    condition = output.enabled_oauth_long_access_connectors["zoom"].source_kind == output.enabled_oauth_long_access_connectors["zoom2"].source_kind
  }
}

# Test 4: Empty configurations
run "empty_enabled_connectors" {
  variables {
    enabled_connectors = []
    enabled_connectors_from_templates = {}
    include_google_workspace = true
    include_msft = true
    msft_tenant_id = "test-tenant-id"
  }

  assert {
    error_message = "Should have no enabled Google Workspace connectors"
    condition = length(output.enabled_google_workspace_connectors) == 0
  }

  assert {
    error_message = "Should have no enabled Microsoft 365 connectors"
    condition = length(output.enabled_msft_365_connectors) == 0
  }

  assert {
    error_message = "Should have no enabled OAuth connectors"
    condition = length(output.enabled_oauth_long_access_connectors) == 0
  }
}

# Test 5: Invalid template IDs should be ignored
run "invalid_template_ids" {
  variables {
    enabled_connectors = []
    enabled_connectors_from_templates = {
      "valid_zoom" = {
        template_id = "zoom"
      }
      "invalid_connector" = {
        template_id = "non-existent-connector"
      }
    }
    include_google_workspace = true
    include_msft = true
    msft_tenant_id = "test-tenant-id"
  }

  assert {
    error_message = "Should have 1 enabled OAuth connector (only valid_zoom)"
    condition = length(output.enabled_oauth_long_access_connectors) == 1
  }

  assert {
    error_message = "valid_zoom connector should be enabled"
    condition = contains(keys(output.enabled_oauth_long_access_connectors), "valid_zoom")
  }

  assert {
    error_message = "invalid_connector should not be enabled"
    condition = !contains(keys(output.enabled_oauth_long_access_connectors), "invalid_connector")
  }
}

# Test 6: Validate connector configurations are properly inherited
run "connector_configuration_inheritance" {
  variables {
    enabled_connectors = []
    enabled_connectors_from_templates = {
      "zoom_custom" = {
        template_id = "zoom"
      }
    }
    include_google_workspace = true
    include_msft = true
    msft_tenant_id = "test-tenant-id"
  }

  assert {
    error_message = "zoom_custom connector should have correct target_host"
    condition = output.enabled_oauth_long_access_connectors["zoom_custom"].target_host == "api.zoom.us"
  }

  assert {
    error_message = "zoom_custom connector should have correct source_auth_strategy"
    condition = output.enabled_oauth_long_access_connectors["zoom_custom"].source_auth_strategy == "oauth2_refresh_token"
  }

  assert {
    error_message = "zoom_custom connector should have correct availability"
    condition = output.enabled_oauth_long_access_connectors["zoom_custom"].availability == "ga"
  }

  assert {
    error_message = "zoom_custom connector should have correct display_name"
    condition = output.enabled_oauth_long_access_connectors["zoom_custom"].display_name == "Zoom"
  }

  assert {
    error_message = "zoom_custom connector should have environment variables"
    condition = length(output.enabled_oauth_long_access_connectors["zoom_custom"].environment_variables) > 0
  }

  assert {
    error_message = "zoom_custom connector should have secured variables"
    condition = length(output.enabled_oauth_long_access_connectors["zoom_custom"].secured_variables) > 0
  }
}
