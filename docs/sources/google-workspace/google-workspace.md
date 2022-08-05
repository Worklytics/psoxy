# Google Workspace

Google Workspace sources can be setup via Terraform, using modules found in our GitHub repo.  These
are included in the examples found in `[infra/examples/](../../infra/examples).

##

## Without Terraform

Instructions for how to setup Google Workspace without terraform:

  1. Create or choose the GCP project in which to create the OAuth Clients.
  2. Activate relevant API(s) in the project.
  3. Create a Service Account and a JSON key for the service account.
  4. Base64-encode the key and store it as a Systems Manager Parameter in AWS (same region as your
     lambda function deployed).  The parameter name should be something like PSOXY_GDIRECTORY_SERVICE_ACCOUNT_KEY.
  5. Get the numeric ID of the service account. Use this plus the oauth scopes to make domain-wide
     delegation grants via the Google Workspace admin console.

NOTE: you could also use a single Service Account for everything, but you will need to store it
repeatedly in AWS for each parameter name.




