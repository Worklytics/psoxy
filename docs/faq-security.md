# FAQ - Security

## Is Psoxy a DLP solution?

Yes, Psoxy supports filtering bulk (flat) files or API responses to remove PII or other sensitive
data prior to transfer to a 3rd party. You configure it with a static set of rules, providing
customizable sanitization behavior of fields. Psoxy supports complex JsonPath expressions if needed,
to perform santization generally across many fields and endpoints.

## Can Psoxy invocation be locked to a set of known IP addresses?

Yes, but only to a broad set of IP blocks that are not exclusive to your Worklytics tenant. As
requests from your Worklytics tenant to your Psoxy instances are authenticated via identity
federation (OIDC) and authorized by your Cloud providers IAM policies, IP-based restrictions are not
necessary.

If you take this approach, you will be responsible for updating your IP restrictions frequently as
GCP changes their IP blocks, or your data flow to Worklytics may break. As such, this is not
officially supported by Worklytics.

Your Worklytics tenant is a process running in GCP, personified by a unique GCP service account. You
simply use your cloud's IAM to grant that service account access to your psoxy instance.

This is functionally equivalent to how access is authenticated and authorized to within and between
any public cloud infrastructure. Eg, access to your S3 buckets is authorized via a policy you
specify in AWS IAM.

Remember that Psoxy is, in effect, a drop-in replacement for a data sources API; in general, these
APIs, such as for Google Workspace, Slack, Zoom, and Microsoft 365, are already accessible from
anywhere on the internet without IP restriction. Psoxy exposes only a more restricted view of the
source API - a subset of its endpoints, http methods (read-only), and fields - with field values
that contain PII redacted or pseudonymized.

See [AWS Authentication and Authorization](aws/authentication-authorization.md) for more details.

See [GCP Authentication and Authorization](gcp/authentication-authorization.md) for more details.

And always remember: an IP is **not** an authenticated identity for a client, and should not be
relied upon as an authentication mechanism. IPs can be spoofed. It is at best an extra control.

## Can Psoxy instances be deployed behind an AWS API Gateway?

Yes - and prior to March 2022 this was necessary. But AWS has released
[Lambda function urls](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html) , which
provide a simpler and more direct way to securely invoke lambdas via HTTP. As such, the
Worklytics-provided Terraform modules use function URLs rather than API gateways.

API gateways provide a layer of indirection that can be useful in certain cases, but is overkill for
psoxy deployments - which do little more than provide a transformed, read-only view of a subset of
endpoints within a data source API. The indirection provides flexibility and control, but at the
cost of complexity in infrastructure and management - as you must provision a gateway, route, stage,
and extra IAM policies to make that all work, compared to a function URL.

That said, the payload lambdas receive when invoked via a function URL is equivalent to the payload
of API Gateway v2, so the proxy itself is compatible with either API Gateway v2 or function urls.

See [API Gateway](aws/guides/api-gateway.md) for more details on how to use Worklytics-provided
terraform modules to enable API gateway in front of your proxy instances.

## Can I deploy a WAF in front of my Psoxy instances?

Sure, but why? Psoxy is itself a rules-based layer that validates requests, authorizes them, and
then sanitizes the response. It is a drop-in replacement for the API of your data source, which in
many cases are publicly exposed to the internet and likely implement their own WAF.

Psoxy never exposes _more_ data than is in the source API itself, and in the usual case it provides
read-only access to a small subset of API endpoints and fields within those endpoints.

Psoxy is stateless, so all requests must go to the source API. Psoxy does not cache or store any
data. There is no database to be vulnerable to SQL injections.

A WAF could make sense if you are using Psoxy to expose an on-prem, in-house built tool to
Worklytics that is otherwise not exposed to the internet.

## Can I deploy Psoxy instances in a VPC?

VPC support is available as a *beta* feature as of February 2024.

VPC usage *requires* an API Gateway to be deployed in front of the proxy instances.

Please note that proxy instances generally use the public APIs of cloud SaaS tools, so do not require
access to your internal network/VPN unless you are connecting to an on-prem tool (eg, GitHub
Enterprise Server, etc).  So there is no technical reason to deploy Psoxy instances in a VPC.

As such, only organizations with inflexible policies requiring such infra to be in a VPC should add
this complexity. Security is best achieved by simplicity and transparency, so deploying VPC
and API Gateway for its own sake does not improve security.

see: [VPC Support](aws/guides/lambdas-on-vpc.md)

## Is Domain-wide Delegation (DWD) for Google Workspace secure?

DWD deserves scrutiny. It is broad grant of data access, generally covering all Google accounts in
your workspace domain. And the UX - pasting a numeric service account ID and a CSV of oauth scopes -
creates potential for errors/exploitation by malicious actors.

To use DWD securely, you must trust the numeric ID; in a typical scenario, where someone or some web
app is asking you to paste this ID into a form, this is a risk. It is NOT a 3-legged oauth flow,
where the redirects between

However, the Psoxy workflow mitigates this risk in several ways:

- DWD grants required for Psoxy connections are made to _your own service accounts, provisioned by
  you and residing in your own GCP project_. They do not belong to a 3rd party. As such you need not
  trust a number shown to you in a web app or email; you can use the GCP web console, CLI, etc to
  confirm the validity of the service account ID independently.
- Your GCP logs can provide transparency into the usage of the service account, to validate what
  data it is being used to access, and from where.
- You remain in control of the only key that can be used to authenticate as the service account -
  you may revoke/rotate this key at any moment should you suspect malicious activity.

Hence, using DWD via Psoxy is more secure than the typical DWD scenario that many security
researchers complain about.

If you remain uncomfortable with DWD, a private Google Marketplace App is a possible alternative,
albeit more tedious to configure. It requires a dedicated GCP project, with additional APIs enabled
in the project.
