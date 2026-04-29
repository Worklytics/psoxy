# migration to drop time_static
# use of `removed` here makes min Terraform version 1.6.x, which as of 0.5.x we allow down to 1.3.x
removed {
  from = time_static.month_start

  lifecycle {
    destroy = true # shouldn't matter
  }

}
