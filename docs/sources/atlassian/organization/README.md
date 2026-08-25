# Atlassian Audit Event / Rovo through Organization API

**Connector ID:** `atlassian-organization`

**Availability:** Beta

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
| `/admin/v1/orgs/{ORG_ID}/events`                          | Retrieve organization audit events           | `read:events:admin`      |
| `/admin/v1/orgs/{ORG_ID}/events-stream`                   | Stream organization audit events             | `read:events:admin`      |
| `/admin/v2/orgs/{ORG_ID}/directories/{DIRECTORY_ID}/users` | Retrieve users from organization directories | `read:directories:admin` |

`{ORG_ID}` is your Atlassian organization id (`connector_settings.atlassian_organization_id`). `{DIRECTORY_ID}` is a directory id, or `-` for all directories (Atlassian wildcard).

## Example API Responses

See the `example-api-responses/original/` directory for sample API responses from each endpoint.

