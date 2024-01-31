
# legacy moves; actually pre 0.4.35, but clearer to split out (know can drop whole file from 0.5)
moved {
  from = module.psoxy-package
  to   = module.psoxy_package
}

moved {
  from = module.test_tool
  to   = module.test_tool[0]
}

# give pseudonym salt a clearer terraform resource id
moved {
  from = random_password.random
  to   = random_password.pseudonym_salt
}
