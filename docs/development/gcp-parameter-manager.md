# GCP Parameter Manager

GCP Parameter Manager is NOT yet implemented as a solution for a config provider.

As of April 2026, it has been dismissed for the following reasons:

- **Heavy change for customers**: It requires significant changes for customers to adopt.
- **IAM Limitations**: It depends on project-level IAM grants. Principle of Least Privilege (PoLP) is only possible with IAM conditions, which is inelegant and hard to test.
- **New API Service**: It requires activating a new API service. It would add to, but not replace, the existing secrets management infrastructure.
- **New IAM Roles and Grants**: It introduces new IAM roles and grants, which would add to, but not replace, the existing secrets configuration.
- **Cost**: It is not a clear win cost-wise.

In addition, **environment variables in GCP** provide plenty of headway for configuration. There is a 32KB limit per environment variable, so by leveraging gzip and base64 encoding for rules, we can increase rule complexity significantly without hitting any limits.
