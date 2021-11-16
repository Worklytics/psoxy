# example-bootstrap-gcp-cft

This is an example configuration of [terraform-google-bootstrap](https://registry.terraform.io/modules/terraform-google-modules/bootstrap/google/latest)
- the official, as sanction by Google + Hashicorp, toolkit for this purpose.

Our example exposes a subset of variables to configure; you can edit `main.tf` to set more.


Worklytics offers a somewhat simplified alternative in `modules/gcp-bootstrap` which may be
sufficient for your purposes - as well as simpler to configure.

We make no endorsement of either as being appropriate for your use-case. Please review both, the
modules on which they depend, and decide what best meets your needs - if any. YMMV


## Known Issues

  - As of Nov 2021, this does NOT run on M1-based Macbooks (eg, `darwin-arm64`) - as has dependencies
which are not available for that platform.




