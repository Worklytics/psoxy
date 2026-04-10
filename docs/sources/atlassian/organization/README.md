# Atlassian Audit Event / Rovo through Organization API

This connector provides access to Atlassian Audit Event for audit logs and directory information, including Atlassian Rovo usage

## Overview

The Atlassian Organization connector enables data collection from:
- **Audit Events**: Organization-level audit events including Rovo agent interactions, user management activities
- **Audit Events Stream**: Real-time stream of audit events
- **Directory Users**: User information from organization directories

## Authentication

This connector uses API token authentication. See the [Atlassian Organization Admin API Authentication documentation](https://developer.atlassian.com/cloud/admin/organization/rest/intro/#auth) for details.

## Required Scopes

- `read:directories:admin`: For retrieving directory users
- `read:events:admin`: For retrieving audit events and audit events stream

## API Endpoints

| Endpoint                                                 | Purpose                                      | Scope Required           |
|----------------------------------------------------------|----------------------------------------------|--------------------------|
| `/admin/v1/orgs/{orgId}/events`                          | Retrieve organization audit events           | `read:events:admin`      |
| `/admin/v1/orgs/{orgId}/events-stream`                   | Stream organization audit events             | `read:events:admin`      |
| `/admin/v2/orgs/{orgId}/directories/{directoryId}/users` | Retrieve users from organization directories | `read:directories:admin` |

## Example API Responses

See the `example-api-responses/original/` directory for sample API responses from each endpoint.

