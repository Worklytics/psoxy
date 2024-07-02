# connector app creation optional; so moving to qualified resource with 0, to allow conditional
# via count hackery (i mean, pattern)
moved {
  from = azuread_application.connector
  to   = azuread_application.connector[0]
}
