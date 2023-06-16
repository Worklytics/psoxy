## Environments

Expectation is that users of terraform modules/examples will want to 'namespace' infra resources
created by a given Terraform configuration in a manner that groups them together via the interfaces
of the host platform, making it easy to find all related infrastructure.

To this end, the convention of an `environment_name` variable will be supported.

As this value will be used to prefix infrastructure resource names/ids in many systems with different
restrictions on allowed characters, we will enforce some universal restrictions on the value, as
specify some transformations:
- restrict characters to `[A-Za-z0-9-_ ]`, starting and ending with an alphabetical character

If generating the name/id of an instance resource would result in a name/id that is too long for the
intended service, then we will truncate the environment id to max length - 6 chars, and generate
a SHA1 hash of the full value to append to the truncated value.

Requirements:
  - avoid resources for different Terraform configurations colliding, even if deployed into same
    AWS account / GCP project. While ideal, it's not always possible to have 1:1 map between
    Terraform configuration and host account/project.
  - easily map resources back to the Terraform configuration that created them
  - have *natural* enumeration of resources from same Terraform configuration accessible via the
    host platforms UX.  (while labels/tags would support enumeration, default sorting in most
    interfaces isn't by label/tag)

Issues:
- leveraging AWS paths, supported for IAM Roles, SSM parameters

### Dismissed Ideas

#### Appending UUIDs/randomized characters

  - it's another resource that shows up in Terraform plan, so that's annoying
  - to have consistent env name everywhere, would need to pass it around Terraform module stack
    making interfaces more verbose / brittle
  - high risk that such an id would end up being generated in two places that need it, each ignorant
    of the other's use-case; having a single 'top-level' place to do it is hierarchical, inconsistent
    with terraform conventions
  - what if you had two Terraform configurations? you'd need to import this value between them to
    have consistent env name


