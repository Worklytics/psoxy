
# uncomment to test this


#resource "aws_kms_key_policy" "key_policy_including_cloudwatch" {
#  key_id = var.project_aws_kms_key_arn
#  policy = jsonencode(
#    {
#      "Version" : "2012-10-17",
#      "Id" : "key-default-1",
#      "Statement" : [
#        {
#          "Sid": "Allow IAM Users to Manage Key",
#          "Effect": "Allow",
#          "Principal": {
#            "AWS": "arn:aws:iam::${var.aws_account_id}:root"
#          },
#          "Action": "kms:*",
#          "Resource": "*"
#        },
#        {
#          "Effect" : "Allow",
#          "Principal" : {
#            "Service" : "logs.${var.aws_region}.amazonaws.com"
#          },
#          "Action" : [
#            "kms:Encrypt",
#            "kms:Decrypt",
#            "kms:ReEncrypt",
#            "kms:GenerateDataKey",
#            "kms:Describe"
#          ],
#          "Resource" : "*"
#        }
#      ]
#    })
#}
