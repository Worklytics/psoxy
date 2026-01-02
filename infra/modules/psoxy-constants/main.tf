
module "env_id" {
  source = "../../modules/env-id"

  environment_name = var.environment_name
}


locals {

  # AWS Managed polices
  # see: https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_managed-vs-inline.html#aws-managed-policies
  required_aws_roles_to_provision_host = {
    "arn:aws:iam::aws:policy/IAMFullAccess"                    = "IAMFullAccess"
    "arn:aws:iam::aws:policy/AmazonS3FullAccess"               = "AmazonS3FullAccess" # only if using bulk sources OR webhook-collection, although 95% do
    "arn:aws:iam::aws:policy/CloudWatchFullAccess"             = "CloudWatchFullAccess"
    "arn:aws:iam::aws:policy/AmazonSSMFullAccess"              = "AmazonSSMFullAccess"
    "arn:aws:iam::aws:policy/AWSLambda_FullAccess"             = "AWSLambda_FullAccess"
    "arn:aws:iam::aws:policy/AmazonSQSFullAccess"              = "AmazonSQSFullAccess"              # only if using webhook-collection
    "arn:aws:iam::aws:policy/AWSKeyManagementServicePowerUser" = "AWSKeyManagementServicePowerUser" # only if using webhook-collection AND using our terraform modules to manage authentication keys
  }
  # AWS managed policy required to consume Microsoft 365 data
  # (in addition to above)
  required_aws_managed_policies_to_consume_msft_365_source = {
    "arn:aws:iam::aws:policy/AmazonCognitoPowerUser" = "AmazonCognitoPowerUser"
  }

  # TODO: could restrict in future, but this is implicit
  account_id_resource_pattern = "*"

  aws_least_privileged_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          # subset of iam:* that we actually need
          # "iam:AddUserToGroup",
          # "iam:AttachGroupPolicy",
          "iam:AttachRolePolicy",
          # "iam:AttachUserPolicy",
          # "iam:CreateAccessKey",
          # "iam:CreateAccountAlias",
          # "iam:CreateGroup",
          # "iam:CreateInstanceProfile",
          # "iam:CreateLoginProfile",
          "iam:CreatePolicy",
          "iam:CreatePolicyVersion",
          "iam:CreateRole",
          # "iam:CreateSAMLProvider",
          # "iam:CreateServiceLinkedRole",
          # "iam:CreateServiceSpecificCredential",
          # "iam:CreateUser",
          # "iam:CreateVirtualMFADevice",
          # "iam:DeactivateMFADevice",
          # "iam:DeleteAccessKey",
          # "iam:DeleteAccountAlias",
          # "iam:DeleteAccountPasswordPolicy",
          # "iam:DeleteGroup",
          # "iam:DeleteGroupPolicy",
          # "iam:DeleteInstanceProfile",
          # "iam:DeleteLoginProfile",
          "iam:DeletePolicy",
          "iam:DeletePolicyVersion",
          "iam:DeleteRole",
          "iam:DeleteRolePermissionsBoundary",
          "iam:DeleteRolePolicy",
          # "iam:DeleteSAMLProvider",
          # "iam:DeleteSSHPublicKey",
          # "iam:DeleteServerCertificate",
          # "iam:DeleteServiceLinkedRole",
          # "iam:DeleteServiceSpecificCredential",
          # "iam:DeleteSigningCertificate",
          # "iam:DeleteUser",
          # "iam:DeleteUserPermissionsBoundary",
          # "iam:DeleteUserPolicy",
          # "iam:DeleteVirtualMFADevice",
          # "iam:DetachGroupPolicy",
          "iam:DetachRolePolicy",
          # "iam:DetachUserPolicy",
          # "iam:EnableMFADevice",
          # "iam:GenerateCredentialReport",
          # "iam:GenerateOrganizationsAccessReport",
          # "iam:GenerateServiceLastAccessedDetails",
          # "iam:GetAccessKeyLastUsed",
          # "iam:GetAccountAuthorizationDetails",
          # "iam:GetAccountPasswordPolicy",
          # "iam:GetAccountSummary",
          # "iam:GetContextKeysForCustomPolicy",
          # "iam:GetContextKeysForPrincipalPolicy",
          # "iam:GetCredentialReport",
          # "iam:GetGroup",
          # "iam:GetGroupPolicy",
          # "iam:GetInstanceProfile",
          # "iam:GetLoginProfile",
          "iam:GetOpenIDConnectProvider",
          # "iam:GetOrganizationsAccessReport",
          "iam:GetPolicy",
          "iam:GetPolicyVersion",
          "iam:GetRole",
          "iam:GetRolePolicy",
          # "iam:GetSAMLProvider",
          # "iam:GetSSHPublicKey",
          # "iam:GetServerCertificate",
          # "iam:GetServiceLastAccessedDetails",
          # "iam:GetServiceLastAccessedDetailsWithEntities",
          # "iam:GetServiceLinkedRoleDeletionStatus",
          # "iam:GetUser",
          # "iam:GetUserPolicy",
          # "iam:ListAccessKeys",
          # "iam:ListAccountAliases",
          # "iam:ListAttachedGroupPolicies",
          "iam:ListAttachedRolePolicies",
          # "iam:ListAttachedUserPolicies",
          "iam:ListEntitiesForPolicy",
          # "iam:ListGroupPolicies",
          # "iam:ListGroups",
          # "iam:ListGroupsForUser",
          # "iam:ListInstanceProfiles",
          # "iam:ListInstanceProfilesForRole",
          # "iam:ListMFADevices",
          "iam:ListOpenIDConnectProviders",
          "iam:ListPolicies",
          "iam:ListPoliciesGrantingServiceAccess",
          "iam:ListPolicyVersions",
          "iam:ListRolePolicies",
          "iam:ListRoles",
          # "iam:ListSAMLProviders",
          # "iam:ListSSHPublicKeys",
          # "iam:ListServerCertificates",
          # "iam:ListServiceSpecificCredentials",
          # "iam:ListSigningCertificates",
          # "iam:ListUserPolicies",
          # "iam:ListUsers",
          # "iam:ListVirtualMFADevices",
          "iam:PassRole", # seems required to attach roles to Lambda functions
          # "iam:PutGroupPolicy",
          "iam:PutRolePermissionsBoundary",
          "iam:PutRolePolicy",
          # "iam:PutUserPermissionsBoundary",
          # "iam:PutUserPolicy",
          "iam:RemoveClientIDFromOpenIDConnectProvider",
          # "iam:RemoveRoleFromInstanceProfile",
          # "iam:RemoveUserFromGroup",
          # "iam:ResetServiceSpecificCredential",
          # "iam:ResyncMFADevice",
          "iam:SetDefaultPolicyVersion",
          # "iam:SetSecurityTokenServicePreferences",
          # "iam:SimulateCustomPolicy",
          # "iam:SimulatePrincipalPolicy",
          # "iam:TagInstanceProfile",
          # "iam:TagMFADevice",
          "iam:TagOpenIDConnectProvider",
          "iam:TagPolicy",
          "iam:TagRole",
          "iam:TagSAMLProvider",
          # "iam:TagServerCertificate",
          # "iam:TagUser",
          # "iam:UntagInstanceProfile",
          # "iam:UntagMFADevice",
          "iam:UntagOpenIDConnectProvider",
          "iam:UntagPolicy",
          "iam:UntagRole",
          # "iam:UntagSAMLProvider",
          # "iam:UntagServerCertificate",
          # "iam:UntagUser",
          # "iam:UpdateAccessKey",
          # "iam:UpdateAccountPasswordPolicy",
          "iam:UpdateAssumeRolePolicy",
          # "iam:UpdateGroup",
          # "iam:UpdateLoginProfile",
          "iam:UpdateOpenIDConnectProviderThumbprint",
          "iam:UpdateRole",
          "iam:UpdateRoleDescription",
          # "iam:UpdateSAMLProvider",
          # "iam:UpdateSSHPublicKey",
          # "iam:UpdateServerCertificate",
          # "iam:UpdateServiceSpecificCredential",
          # "iam:UpdateSigningCertificate",
          # "iam:UpdateUser",
          # "iam:UploadSSHPublicKey",
          # "iam:UploadServerCertificate",
          # "iam:UploadSigningCertificate",
        ]
        Resource = [
          "arn:aws:iam::${local.account_id_resource_pattern}:policy/${module.env_id.id}*",
          "arn:aws:iam::${local.account_id_resource_pattern}:role/${module.env_id.id}*"
        ]
      },
      {
        Sid    = "OrganizationsRead"
        Effect = "Allow"
        Action = [
          "organizations:DescribeAccount",
          "organizations:DescribeOrganization",
          "organizations:DescribeOrganizationalUnit",
          "organizations:DescribePolicy",
          "organizations:ListChildren",
          "organizations:ListParents",
          "organizations:ListPolicies",
          "organizations:ListPoliciesForTarget",
          "organizations:ListRoots",
          "organizations:ListTargetsForPolicy"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          # subset of s3:* that we actually need
          "s3:AbortMultipartUpload",
          "s3:CreateBucket",
          "s3:DeleteBucket",
          "s3:DeleteBucketPolicy",
          "s3:DeleteObject",
          "s3:DeleteObjectTagging",
          "s3:DeleteObjectVersion",
          "s3:DeleteObjectVersionTagging",
          "s3:GetAccelerateConfiguration",
          "s3:GetAnalyticsConfiguration",
          "s3:GetBucketAcl",
          "s3:GetBucketCORS",
          "s3:GetBucketLocation",
          "s3:GetBucketLogging",
          "s3:GetBucketNotification",
          "s3:GetBucketObjectLockConfiguration",
          "s3:GetBucketPolicy",
          "s3:GetBucketPolicyStatus",
          "s3:GetBucketPublicAccessBlock",
          "s3:GetBucketRequestPayment",
          "s3:GetBucketTagging",
          "s3:GetBucketVersioning",
          "s3:GetBucketWebsite",
          "s3:GetEncryptionConfiguration",
          "s3:GetInventoryConfiguration",
          "s3:GetLifecycleConfiguration",
          "s3:GetMetricsConfiguration",
          "s3:GetObject",
          "s3:GetObjectAcl",
          "s3:GetObjectLegalHold",
          "s3:GetObjectRetention",
          "s3:GetObjectTagging",
          # "s3:GetObjectTorrent",
          "s3:GetObjectVersion",
          "s3:GetObjectVersionAcl",
          "s3:GetObjectVersionForReplication",
          "s3:GetObjectVersionTagging",
          # "s3:GetObjectVersionTorrent",
          "s3:GetReplicationConfiguration",
          # "s3:ListAllMyBuckets",
          "s3:ListBucket", # don't love this, but seems needed
          # "s3:ListBucketByTags",
          # "s3:ListBucketMultipartUploads",
          # "s3:ListBucketVersions",
          # "s3:ListMultipartUploadParts",
          # "s3:PutAccelerateConfiguration",
          # "s3:PutAnalyticsConfiguration",
          "s3:PutBucketAcl",
          "s3:PutBucketCORS",
          "s3:PutBucketLogging",
          "s3:PutBucketNotification",
          "s3:PutBucketObjectLockConfiguration",
          "s3:PutBucketPolicy",
          "s3:PutBucketPublicAccessBlock",
          # "s3:PutBucketRequestPayment",
          "s3:PutBucketTagging",
          "s3:PutBucketVersioning",
          # "s3:PutBucketWebsite",
          "s3:PutEncryptionConfiguration",
          # "s3:PutInventoryConfiguration",
          "s3:PutLifecycleConfiguration",
          # "s3:PutMetricsConfiguration",
          "s3:PutObject"
        ]
        Resource = [
          "arn:aws:s3::${local.account_id_resource_pattern}:${module.env_id.id}*",
          "arn:aws:s3::${local.account_id_resource_pattern}:${module.env_id.id}*/*"
        ]
      },
      {
        Sid : "CloudWatchLogsAccess",
        Effect : "Allow",
        Action : [
          "logs:CreateLogGroup",
          "logs:DeleteLogGroup",
          "logs:DescribeLogGroups",
          # "logs:DescribeLogStreams",
          # "logs:PutLogEvents",
          # "logs:DeleteLogStream",
          # "logs:CreateLogStream",
          "logs:PutRetentionPolicy",
          "logs:DeleteRetentionPolicy",
          "logs:DescribeResourcePolicies",
          # "logs:DescribeSubscriptionFilters",
          # "logs:PutSubscriptionFilter",
          # "logs:DeleteSubscriptionFilter",
          "logs:PutResourcePolicy",
          "logs:DeleteResourcePolicy",
          "logs:ListTagsLogGroup",
          "logs:TagLogGroup",
          "logs:UntagLogGroup"
        ],
        Resource : [
          "arn:aws:logs:*:${local.account_id_resource_pattern}:log-group:/aws/lambda/${module.env_id.id}*",
          # seems how needed for initial log groups; uses 'logs:DescribeLogGroups' to enumerate streams, it seems
          "arn:aws:logs:*:${local.account_id_resource_pattern}:log-group::log-stream*",
        ]
      },
      {
        # seems this needed by Terraform on account-level; avoids error like the following:
        # Error: describing SSM parameter (LST-PRIV_PSOXY_ENCRYPTION_KEY): AccessDeniedException: User: arn:aws:sts::123123123123123:assumed-role/MinProvisioner/aws-go-sdk-1717785438809132000 is not authorized to perform: ssm:DescribeParameters on resource: arn:aws:ssm:us-west-1:874171213677:* because no identity-based policy allows the ssm:DescribeParameters action
        Sid : "SSMParameterAccessAccountLevel",
        Effect : "Allow",
        Action : [
          "ssm:DescribeParameters",
        ]
        Resource : "arn:aws:ssm:*:${local.account_id_resource_pattern}:*"
      },
      {
        Sid : "SSMParameterAccess",
        Effect : "Allow",
        Action : [
          "ssm:AddTagsToResource",
          "ssm:DeleteParameter",
          "ssm:DeleteParameters",
          "ssm:GetParameter",
          "ssm:GetParameterHistory",
          "ssm:GetParameters",
          "ssm:GetParametersByPath",
          "ssm:GetResourcePolicies",
          "ssm:LabelParameterVersion",
          "ssm:ListTagsForResource",
          "ssm:PutParameter",
          "ssm:PutResourcePolicy",
          "ssm:RemoveTagsFromResource",
          "ssm:UnlabelParameterVersion",
        ],
        # convention here that SSM parameters are prefixed with the environment name
        Resource : "arn:aws:ssm:*:${local.account_id_resource_pattern}:parameter/${upper(module.env_id.id)}*"
      },
      {
        Sid    = "LambdaAccess"
        Effect = "Allow"
        Action = [
          # subset of lambda:* that we actually need
          "lambda:AddPermission",
          "lambda:CreateFunction",
          "lambda:DeleteFunction",
          "lambda:GetPolicy",
          "lambda:GetFunction",
          "lambda:GetFunctionConfiguration",
          "lambda:GetEventSourceMapping",
          "lambda:GetFunctionEventInvokeConfig",
          "lambda:GetFunctionConcurrency",
          "lambda:GetProvisionedConcurrencyConfig",
          "lambda:GetFunctionCodeSigningConfig",

          "lambda:ListVersionsByFunction",
          "lambda:ListProvisionedConcurrencyConfigs",
          "lambda:PutFunctionConcurrency",
          "lambda:PutFunctionEventInvokeConfig",
          "lambda:PutProvisionedConcurrencyConfig",
          "lambda:RemovePermission",

          # "lambda:InvokeFunction",
          "lambda:ListFunctions",
          "lambda:UpdateFunctionCode",
          "lambda:UpdateFunctionConfiguration",
          "lambda:UpdateEventSourceMapping",
          "lambda:DeleteEventSourceMapping",
          "lambda:DeleteFunctionEventInvokeConfig",
          "lambda:DeleteFunctionConcurrency",
          "lambda:TagResource", # required if using default_tags

          # can drop these if using API gateway stuff
          "lambda:GetFunctionUrlConfig",
          "lambda:ListFunctionUrlConfigs",
          "lambda:UpdateFunctionUrlConfig",
          "lambda:DeleteFunctionUrlConfig",
          "lambda:CreateFunctionUrlConfig",

        ]
        Resource = "arn:aws:lambda:*:${local.account_id_resource_pattern}:function:${module.env_id.id}*"
      },
      {
        Sid    = "SQSAccess"
        Effect = "Allow"
        Action = [
          "sqs:CreateQueue",
          "sqs:DeleteQueue",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl",
          "sqs:ListQueues",
          "sqs:ListQueueTags",
          "sqs:SetQueueAttributes",
          "sqs:AddPermission",
          "sqs:RemovePermission",
          "sqs:TagQueue",
          "sqs:UntagQueue"
        ]
        Resource = "arn:aws:sqs:*:${local.account_id_resource_pattern}:${module.env_id.id}*"
      },
      { # need to provision KMS keys, aliases
        Sid    = "KMSAccess"
        Effect = "Allow"
        Action = [
          "kms:CreateKey",
          "kms:ScheduleKeyDeletion",
          "kms:CancelKeyDeletion",
          "kms:DescribeKey",
          "kms:GetKeyPolicy",
          "kms:PutKeyPolicy",
          "kms:ListKeyPolicies",
          "kms:ListResourceTags",
          "kms:TagResource",
          "kms:UntagResource",
          "kms:CreateAlias",
          "kms:UpdateAlias",
          "kms:DeleteAlias",
          "kms:ListAliases"
        ]
        Resource = "arn:aws:kms:*:${local.account_id_resource_pattern}:*" # kms key ids are random UUIDs, so can't use env_id prefix to constrain
      }
    ]
  })

  # subset of https://docs.aws.amazon.com/aws-managed-policy/latest/reference/SecretsManagerReadWrite.html
  # as that seems like overkill
  #  - if you're going to use KMS to encrypt the secrets, then you'll need to add the KMS permissions
  #    on the key you intend to use.
  #  - you can/should modify the Resource part of this to limit to a subset of secrets, if this
  #    is being deployed to an AWS account that's used for purposes beyond this proxy deployment
  required_aws_policy_to_use_secrets_manager = {
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Action" : [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
          "secretsmanager:ListSecrets",
          "secretsmanager:ListSecretVersionIds",
          "secretsmanager:GetResourcePolicy",
          "secretsmanager:ListTagsForResource",
          # "secretsmanager:GetRandomPassword",
          "secretsmanager:CreateSecret",
          "secretsmanager:UpdateSecret",
          "secretsmanager:DeleteSecret",
          "secretsmanager:PutSecretValue",
          "secretsmanager:RestoreSecret",
          "secretsmanager:ReplicateSecretToRegions",
          "secretsmanager:RemoveRegionsFromReplication",
          "secretsmanager:TagResource",
          "secretsmanager:UntagResource",
          # "secretsmanager:RotateSecret",
          # "secretsmanager:CancelRotateSecret",
          "secretsmanager:UpdateSecretVersionStage",
          # "secretsmanager:ModifyRotationRules",
          # "secretsmanager:RotateImmediately",
          # "secretsmanager:PutResourcePolicy",
          # "secretsmanager:DeleteResourcePolicy",
          # "secretsmanager:ValidateResourcePolicy",
          "secretsmanager:StopReplicationToReplica"
        ],
        "Resource" : "arn:aws:secretsmanager:*:*:secret:${module.env_id.id}*"
      }
    ]
  }


  # TODO: create IAM policy document, which installer could use to create their own policy as
  # alternative to using AWS Managed policies

  # initial GCP APIs that must be enabled in projects that will host the proxy.
  # (Terraform apply will enabled additional ones)
  required_gcp_apis_to_host = {
    # https://console.cloud.google.com/apis/library/iamcredentials.googleapis.com
    "iamcredentials.googleapis.com" = "IAM Service Account Credentials API",
    # https://console.cloud.google.com/apis/library/serviceusage.googleapis.com
    "serviceusage.googleapis.com" = "Service Usage API",
  }

  required_gcp_perms_to_provision_webhook_collectors = [
    "pubsub.topics.create",
    "pubsub.topics.get",
    "pubsub.topics.list",
    "pubsub.subscriptions.create",
    "pubsub.subscriptions.get",
    "pubsub.subscriptions.list",
    "cloudscheduler.jobs.create",
    "cloudscheduler.jobs.get",
    "cloudscheduler.jobs.list",
  ]

  required_gcp_roles_to_provision_host = merge({
    "roles/storage.admin" = {
      display_name    = "Storage Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/storage#storage.admin"
    },
    "roles/iam.roleAdmin" = {
      display_name    = "IAM Role Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/iam#iam.roleAdmin"
    },
    "roles/resourcemanager.projectIamAdmin" = {
      display_name    = "Project IAM Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/resourcemanager#resourcemanager.projectIamAdmin"
    }
    "roles/secretmanager.admin" = {
      display_name    = "Secret Manager Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/secretmanager#secretmanager.admin"
    },
    "roles/iam.serviceAccountAdmin" = {
      display_name    = "Service Account Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/iam#iam.serviceAccountAdmin"
    },
    "roles/serviceusage.serviceUsageAdmin" = {
      display_name    = "Service Usage Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/serviceusage#serviceusage.serviceUsageAdmin"
    },
    "roles/cloudfunctions.developer" = {
      display_name    = "Cloud Functions Developer",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/cloudfunctions#cloudfunctions.developer"
    },
    "roles/run.admin" = {
      display_name    = "Cloud Run Admin",
      description_url = "https://docs.cloud.google.com/iam/docs/roles-permissions/run#run.admin"
    },
    "roles/compute.viewer" = {
      display_name    = "Compute Viewer",
      description_url = "https://docs.cloud.google.com/iam/docs/roles-permissions/compute#compute.viewer"
    },
  }, local.required_gcp_roles_to_provision_webhook_collectors)

  required_gcp_perms_to_provision_host = concat([
    # Project IAM administration
    "resourcemanager.projects.get",
    "resourcemanager.projects.getIamPolicy",
    "resourcemanager.projects.setIamPolicy",

    # Service Usage API management
    "serviceusage.services.enable",
    "serviceusage.services.get",
    "serviceusage.services.list",

    # IAM - custom roles and service accounts
    "iam.roles.create",
    "iam.roles.delete",
    "iam.roles.get",
    "iam.roles.list",
    "iam.roles.undelete",
    "iam.roles.update",
    "iam.serviceAccounts.actAs",
    "iam.serviceAccounts.create",
    "iam.serviceAccounts.delete",
    "iam.serviceAccounts.get",
    "iam.serviceAccounts.getIamPolicy",
    "iam.serviceAccounts.list",
    "iam.serviceAccounts.setIamPolicy",
    "iam.serviceAccounts.update",

    # Secret Manager
    "secretmanager.secrets.create",
    "secretmanager.secrets.delete",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.setIamPolicy",
    "secretmanager.secrets.update",
    # TBC: grant terrafrom SA the perms/roles to manage secret versions on those secrets it needs to manage
    # "secretmanager.versions.access",
    # "secretmanager.versions.add",
    # "secretmanager.versions.destroy",
    # "secretmanager.versions.disable",
    # "secretmanager.versions.enable",
    # "secretmanager.versions.get",
    # "secretmanager.versions.list",

    # Cloud Storage
    "storage.buckets.create",
    "storage.buckets.delete",
    "storage.buckets.get",
    "storage.buckets.getIamPolicy",
    "storage.buckets.list",
    "storage.buckets.setIamPolicy",
    "storage.buckets.update",
    # TBC: rather than grant these perms at project level, better for SA to grant itself objectAdmin on the buckets it needs to write to, right??
    # "storage.objects.create",
    # "storage.objects.delete",
    # "storage.objects.get",
    # "storage.objects.list",
    # "storage.objects.update",
    "storage.serviceAccounts.get",

    # Cloud Functions (Gen 2) and Cloud Run
    "cloudfunctions.functions.create",
    "cloudfunctions.functions.delete",
    "cloudfunctions.functions.get",
    "cloudfunctions.functions.list",
    "cloudfunctions.functions.setIamPolicy",
    "cloudfunctions.functions.update",
    "cloudfunctions.locations.list",
    "cloudfunctions.operations.get",
    "run.locations.list",
    "run.operations.get",
    "run.services.get",
    "run.services.getIamPolicy",
    "run.services.list",
    "run.services.setIamPolicy",
    "run.services.update",

    # Artifact Registry
    "artifactregistry.locations.get",
    "artifactregistry.repositories.get",
    "artifactregistry.repositories.list",

    # Compute Engine (read-only for metadata access)
    "compute.projects.get",
    "compute.zones.get",
    "compute.zones.list",
  ], local.required_gcp_perms_to_provision_webhook_collectors)

  # required roles to provision webhook collectors, beyond the core ones needed for any GCP instance
  required_gcp_roles_to_provision_webhook_collectors = {
    "roles/pubsub.admin" = {
      display_name    = "Pub/Sub Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/pubsub#pubsub.admin"
    },
    "roles/cloudscheduler.admin" = {
      display_name    = "Cloud Scheduler Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/cloudscheduler#cloudscheduler.admin"
    }
  }

  # assumes you actually need to create the subnetwork, VPC serverless connector; if your VPC, subnetwork, and connector exist,
  # you probably can avoid
  required_gcp_roles_to_use_vpc = {
    "roles/compute.networkAdmin" = {
      display_name    = "Compute Network Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/compute#compute.networkAdmin"
    },
    "roles/vpcaccess.admin" = {
      display_name    = "VPC Access Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/vpcaccess#vpcaccess.admin"
    }
  }

  # TODO: add list of permissions, which customer could use to create custom role as alternative
  min_gcp_permissions_to_host = toset([
    # Project IAM administration
    "resourcemanager.projects.get",
    "resourcemanager.projects.getIamPolicy",
    "resourcemanager.projects.setIamPolicy",

    # Service Usage API management
    "serviceusage.services.enable",
    "serviceusage.services.get",
    "serviceusage.services.list",

    # IAM - custom roles and service accounts
    "iam.roles.create",
    "iam.roles.delete",
    "iam.roles.get",
    "iam.roles.list",
    "iam.roles.undelete",
    "iam.roles.update",
    "iam.serviceAccountKeys.create",
    "iam.serviceAccountKeys.delete",
    "iam.serviceAccountKeys.get",
    "iam.serviceAccounts.actAs",
    "iam.serviceAccounts.create",
    "iam.serviceAccounts.delete",
    "iam.serviceAccounts.get",
    "iam.serviceAccounts.getIamPolicy",
    "iam.serviceAccounts.list",
    "iam.serviceAccounts.setIamPolicy",
    "iam.serviceAccounts.update",

    # Secret Manager
    "secretmanager.secrets.create",
    "secretmanager.secrets.delete",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.setIamPolicy",
    "secretmanager.secrets.update",
    "secretmanager.versions.access",
    "secretmanager.versions.add",
    "secretmanager.versions.destroy",
    "secretmanager.versions.disable",
    "secretmanager.versions.enable",
    "secretmanager.versions.get",
    "secretmanager.versions.list",

    # Cloud Storage
    "storage.buckets.create",
    "storage.buckets.delete",
    "storage.buckets.get",
    "storage.buckets.getIamPolicy",
    "storage.buckets.list",
    "storage.buckets.setIamPolicy",
    "storage.buckets.update",
    "storage.objects.create",
    "storage.objects.delete",
    "storage.objects.get",
    "storage.objects.list",
    "storage.objects.update",
    "storage.serviceAccounts.get",

    # Cloud Functions (Gen 2) and Cloud Run
    "cloudfunctions.functions.create",
    "cloudfunctions.functions.delete",
    "cloudfunctions.functions.get",
    "cloudfunctions.functions.list",
    "cloudfunctions.functions.setIamPolicy",
    "cloudfunctions.functions.update",
    "cloudfunctions.locations.list",
    "cloudfunctions.operations.get",
    "run.locations.list",
    "run.operations.get",
    "run.services.get",
    "run.services.getIamPolicy",
    "run.services.list",
    "run.services.setIamPolicy",
    "run.services.update",

    # Artifact Registry
    "artifactregistry.locations.get",
    "artifactregistry.repositories.get",
    "artifactregistry.repositories.list",

    # Pub/Sub
    "pubsub.subscriptions.create",
    "pubsub.subscriptions.delete",
    "pubsub.subscriptions.get",
    "pubsub.subscriptions.getIamPolicy",
    "pubsub.subscriptions.list",
    "pubsub.subscriptions.setIamPolicy",
    "pubsub.subscriptions.update",
    "pubsub.topics.create",
    "pubsub.topics.delete",
    "pubsub.topics.get",
    "pubsub.topics.getIamPolicy",
    "pubsub.topics.list",
    "pubsub.topics.setIamPolicy",
    "pubsub.topics.update",

    # Cloud Scheduler - async mode, webhooks
    "cloudscheduler.jobs.create",
    "cloudscheduler.jobs.delete",
    "cloudscheduler.jobs.get",
    "cloudscheduler.jobs.list",
    "cloudscheduler.jobs.pause",
    "cloudscheduler.jobs.resume",
    "cloudscheduler.jobs.run",
    "cloudscheduler.jobs.update",

    # Cloud KMS
    "cloudkms.cryptoKeyVersions.get",
    "cloudkms.cryptoKeyVersions.list",
    "cloudkms.cryptoKeyVersions.viewPublicKey",
    "cloudkms.cryptoKeys.create",
    "cloudkms.cryptoKeys.get",
    "cloudkms.cryptoKeys.getIamPolicy",
    "cloudkms.cryptoKeys.list",
    "cloudkms.cryptoKeys.setIamPolicy",
    "cloudkms.keyRings.create",
    "cloudkms.keyRings.get",
    "cloudkms.keyRings.list",
    "cloudkms.keyRings.setIamPolicy",

    # Eventarc (bulk collectors)
    "eventarc.locations.list",
    "eventarc.triggers.create",
    "eventarc.triggers.delete",
    "eventarc.triggers.get",
    "eventarc.triggers.list",
    "eventarc.triggers.update",

    # # VPC Access connectors (optional network egress)
    # "vpcaccess.connectors.create",
    # "vpcaccess.connectors.delete",
    # "vpcaccess.connectors.get",
    # "vpcaccess.connectors.list",
    # "vpcaccess.connectors.update",

    # # VPC network lookups referenced by connectors
    # "compute.networks.get",
    # "compute.subnetworks.get",
  ])




  # TODO: confirm that this is indeed the same list (believe it is)
  required_gcp_apis_to_provision_google_workspace_source = local.required_gcp_apis_to_host

  required_gcp_roles_to_provision_google_workspace_source = {
    "roles/iam.serviceAccountAdmin" = {
      display_name    = "Service Account Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/iam#iam.serviceAccountAdmin"
    },
    "roles/serviceusage.serviceUsageAdmin" = {
      display_name    = "Service Usage Admin",
      description_url = "https://cloud.google.com/iam/docs/roles-permissions/serviceusage#serviceusage.serviceUsageAdmin"
    }
  }

  # Permissions required to provision Google Workspace connectors (service account keys)
  required_gcp_perms_to_provision_google_workspace_source = [
    "iam.serviceAccountKeys.create",
    "iam.serviceAccountKeys.delete",
    "iam.serviceAccountKeys.get",
  ]

  required_azuread_roles_to_provision_msft_365_source = {
    "7ab1d382-f21e-4acd-a863-ba3e13f7da61" = "Cloud Application Administrator",
  }
}
