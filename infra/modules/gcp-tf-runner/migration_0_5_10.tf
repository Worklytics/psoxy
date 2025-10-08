moved {
  from = data.google_client_openid_userinfo.identity
  to   = data.google_client_openid_userinfo.identity[0]
}