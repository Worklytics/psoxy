
moved {
  from = data.azuread_users.owners
  to   = data.azuread_users.owners[0]
}

moved {
  from = data.azuread_client_config.current
  to   = data.azuread_client_config.current[0]
}
