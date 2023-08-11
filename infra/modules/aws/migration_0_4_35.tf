
# give pseudonym salt a clearer terraform resource id
moved {
  from = random_password.random
  to   = random_password.pseudonym_salt
}
