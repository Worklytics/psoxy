variable "project_id" {
  type        = string
  description = "id of GCP project that will host psoxy instance"
}

variable "gcp_region" {
  type        = string
  description = "Region in which to provision GCP resources, if applicable"
  default     = "us-central1"
}

variable "bucket_location" {
  type        = string
  description = "location of bucket that will be used to store Psoxy artifacts"
  default     = "us-central1"
}

variable "secret_replica_locations" {
  type        = list(string)
  description = "List of locations to which to replicate secrets. See https://cloud.google.com/secret-manager/docs/locations"
  default = [
    "us-central1",
    "us-west1",
  ]
}

variable "psoxy_base_dir" {
  type        = string
  description = "the path where your psoxy repo resides"
  default     = "../../.."

  validation {
    condition     = fileexists(format("%sjava/pom.xml", var.psoxy_base_dir))
    error_message = "The psoxy_base_dir value should be a path to a directory containing java/pom.xml."
  }
}

variable "deployment_bundle" {
  type        = string
  description = "path to deployment bundle to use (if not provided, will build one). Can be a local file path or GCS URL (e.g., 'gs://psoxy-public-artifacts/psoxy-0.4.28.zip')."
  default     = null
}

variable "force_bundle" {
  type        = bool
  description = "whether to force build of deployment bundle, even if it already exists"
  default     = false
}

variable "psoxy_version" {
  type        = string
  description = "IGNORED; version of psoxy to deploy"
  default     = null
}

variable "environment_id_prefix" {
  type        = string
  description = "A prefix to give to all resources created/consumed by this module."
  default     = ""
}

variable "config_parameter_prefix" {
  type        = string
  description = "A prefix to give to all config parameters (GCP Secret Manager Secrets) created/consumed by this module."
  default     = ""
}

variable "install_test_tool" {
  type        = bool
  description = "whether to install the test tool (can be 'false' if Terraform not running from a machine where you intend to run tests of your Psoxy deployment)"
  default     = true
}

variable "custom_artifacts_bucket_name" {
  type        = string
  description = "name of bucket to use for custom artifacts, if you want something other than default"
  default     = null
}

variable "default_labels" {
  type        = map(string)
  description = "*Alpha* in v0.4, only respected for new resources. Labels to apply to all resources created by this configuration. Intended to be analogous to AWS providers `default_tags`."
  default     = {}
}

variable "support_bulk_mode" {
  type        = bool
  description = "whether to enable/provision components required for 'bulk mode' instances"
  default     = true
}

variable "support_webhook_collectors" {
  type        = bool
  description = "whether to enable/provision components required for 'webhook collectors' instances"
  default     = false
}

variable "vpc_config" {
  type = object({
    # Local name of the VPC network resource on which to provision the VPC connector (if `serverless_connector` is not provided)
    # For Shared VPC, use the full self_link to the network in the host project
    network                         = optional(string)
    # Local name of the VPC subnet resource on which to provision the VPC connector (if `serverless_connector` is not provided).
    # For Shared VPC, use the full self_link of the subnetwork in the host project
    # NOTE: Subnet MUST have /28 netmask (required by Google Cloud for VPC connectors)
    subnet                          = optional(string)
    # Format: projects/{project}/locations/{location}/connectors/{connector}
    # If set, everything else will be ignored
    serverless_connector            = optional(string)
  })

  description = "**beta** configuration of a VPC to be used by the Psoxy instances, if any (null for none)."
  default     = null

  validation {
    condition = (
    # no config at all
    var.vpc_config == null ||
    # serverless connector referenced
    (try(var.vpc_config.serverless_connector, null) != null) &&
    # network and subnetwork defined
    (
    (try(var.vpc_config.serverless_connector, null) == null) &&
    (try(var.vpc_config.network, null) != null) &&
    (try(var.vpc_config.subnet, null) != null) &&
    ((!startswith(var.vpc_config.network, "projects/") && !startswith(var.vpc_config.subnet, "projects/")) ||
    ( startswith(var.vpc_config.network, "projects/") &&  startswith(var.vpc_config.subnet, "projects/")))
    )
    )
    error_message = "Invalid vpc_config: Must provide either serverless_connector, or valid network/subnet as described in the documentation."
  }

}

variable "bucket_force_destroy" {
  type        = bool
  description = "set the `force_destroy` flag on each google_storage_bucket provisioned by this module"
  default     = false
}

variable "tf_runner_iam_principal" {
  description = "The IAM principal (e.g., 'user:alice@example.com' or 'serviceAccount:terraform@project.iam.gserviceaccount.com') that Terraform is running as, used for granting necessary permissions to provision Cloud Functions."
  type        = string
}

variable "provision_project_level_iam" {
  description = "Whether to provision project-level IAM bindings required for Psoxy operation. This includes granting the Pub/Sub Publisher role to the GCS default service account and the Cloud Build Builder role to the Compute Engine default service account. Set to false if you prefer to manage these IAM bindings outside of Terraform."
  type        = bool
  default     = true
}
