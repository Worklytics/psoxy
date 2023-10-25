# FAQ - Security


## Can Psoxy invocation be locked to a set of known IP addresses?

No, but this is not necessary, as requests from your Worklytics tenant to your Psoxy instances are
authenticated via identity federation (OIDC) and authorized by your Cloud providers IAM policies.

Your Worklytics tenant is a process running in GCP, personified by a unique GCP service account. You
simply use your cloud's IAM to grant that service account access to your psoxy instance.

This is functionally equivalent to how access is authenticated and authorized to within and between
any public cloud infrastructure. Eg, access to your S3 buckets is authorized via a policy you specify
in AWS IAM.

Remember that Psoxy is, in effect, a drop-in replacement for a data sources API; in general, these
APIs, such as for Google Workspace, Slack, Zoom, and Microsoft 365, are already accessible from
anywhere on the internet without IP restriction.  Psoxy exposes only a more restricted view of the
source API - a subset of its endpoints, http methods (read-only), and fields - with field values that
contain PII redacted or pseudonymized.

See [AWS Authentication and Authorization](aws/authentication-authorization.md) for more details.

See [GCP Authentication and Authorization](gcp/authentication-authorization.md) for more details.

## Can Psoxy instances be deployed behind an AWS API Gateway?

Yes - and prior to March 2022 this was necessary. But AWS has released [Lambda function urls](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html)
, which provide a simpler and more direct way to securely invoke lambdas via HTTP.  As such, the
Worklytics-provided Terraform modules use function URLs rather than API gateways.

API gateways provide a layer of indirection that can be useful in certain cases, but is overkill for
psoxy deployments - which do little more than provide a transformed, read-only view of a subset of
endpoints within a data source API.  The indirection provides flexibility and control, but at the
cost of complexity in infrastructure and management - as you must provision a gateway, route, stage,
and extra IAM policies to make that all work, compared to a function URL.

That said, the payload lambdas receive when invoked via a function URL is equivalent to the payload
of API Gateway v2, so the proxy itself is compatible with either API Gateway v2 or function urls.

## Can I deploy a WAF in front of my Psoxy instances?

Sure, but why? Psoxy is itself a rules-based layer that validates requests, authorizes them, and
then sanitizes the response. It is a drop-in replacement for the API of your data source, which in
many cases are publicly exposed to the internet and likely implement their own WAF.

Psoxy never exposes *more* data than is in the source API itself, and in the usual case it provides
read-only access to a small subset of API endpoints and fields within those endpoints.

Psoxy is stateless, so all requests must go to the source API.  Psoxy does not cache or store any
data. There is no database to be vulnerable to SQL injections.

A WAF could make sense if you are using Psoxy to expose an on-prem, in-house built tool to
Workltyics that is otherwise not exposed to the internet.

