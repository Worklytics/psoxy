# GCP Trouble Shooting

Tips and tricks for using GCP as to host the proxy.


## GCloud CLI client blocked by Organization policy

Some orgs have policies that block authentication of the GCloud CLI client, requiring you to contact
your IT team and have it added to an approved list. Apart from that, there are several possibilities:

  1. use the GCP Cloud Shell (via GCP web console). `gcloud` is pre-installed and pre-authorized as
     your Google user in the Cloud Shell.
  2. use a VM in GCP Compute Engine, with the VM running as a sufficiently privileged service
     account. In such a scenario, `gcloud` will be pre-authenticated by GCP on the VM as that
     service account.
  3. create credentials within the project itself:
      - enable IAM API and Cloud Resource Manager API within the project
      - create OAuth credentials for a 'desktop application' within the target GCP project
      - download the `client-secrets.json` file to your  environment
      - run `gcloud auth application-default login --client-id-file=/path/to/client-secrets.json`



## GCP rejects calls because APIs disabled on target project

Terraform relies on GCP's REST APIs for its operations. If these APIs are disabled either the target
project OR the project in which the identity (service account, OAuth client) under which you're
running terraform resides, you may get an error.

The solution is to enable APIs via the Cloud Console, specifically:

  * IAM API
  * Cloud Resource Manager API
