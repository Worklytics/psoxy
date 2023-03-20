**DEPRECATED - DOES NOT ACTUALLY WORK AS ADVERTISED**

# aws-psoxy-bulk-existing

This module provisions an AWS Lambda that sanitizes files uploaded into an *existing* bucket
according to its rules. It can be used to piggyback of an existing pipeline flow to produce an
alternative version of the file based on distinct sanitization rules.

For example, if you are sending an export of your HRIS data to Worklytics every week and wish to
build a *lookup table* to re-identify data in your premises - you could build that table from the
same bucket to which you already export your HRIS data, but with a different rule set that preserves
employee ids in the clear, in addition to the pseudonymized form.

For example usage, see: [modular-examples/aws-msft-365/main.tf](../../modular-examples/aws-msft-365/main.tf)
