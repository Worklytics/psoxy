## Testing

By default, the Terraform examples provided by Worklytics install a NodeJS-based tool for testing
your proxy deployments.

Full documentation of the test tool is available [here](../tools/psoxy-test/README.md).

### Testing Prerequisities

The

Wherever you run this test tool from, your AWS or GCloud CLI *must* be authenticated as
an entity with permissions to invoke the Lambda functions / Cloud functions that you deployed for
Psoxy.

If you're testing the bulk cases, the entity must be able to read/write to the cloud storage
buckets created for each of those bulk examples.





### Testing Locally when Terraform ran remotely (eg, Terraform Cloud, GitHub Actions, etc)

If you're running the Terraform examples in a different location from where
you wish to run tests, then you can install the tool alone:

1. Clone the Psoxy repo to your local machine:

```shell
git clone https://github.com/Worklytics/psoxy.git
```

2. From within that clone, install the test tool:

```shell
./tools/install-test-tool.sh
```

3. Review the `todos_2` output variable from the Terraform apply run, which will contain specific
   examples of how to run the test tool for your deployment.

   The contents of this output variable are also written to the local file system of where you ran
   Terraform apply, with prefixes `TODO 2 ...`



### Testing Deployments made without Terraform

If you used and approach other than Terraform, or did not directly use our Terraform examples, you
may not have the testing examples or the test tool installed on your machine.

In such a case, you can install the test tool manually by following steps 1+2 above, and then can
review the documentation on how to use it from your machine.
